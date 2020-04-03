# springfun

POC of a CLI for creating Spring Functions to run on Knative

## Prerequisites:

* a [Kubernetes](https://kubernetes.io/) cluster and the [kubectl CLI](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
* [curl](https://curl.haxx.se/) command
* [Java 11 JDK](https://adoptopenjdk.net/installation.html?variant=openjdk11#)
* [Skaffold](https://skaffold.dev/)
* [Docker](https://www.docker.com/)
* [pack CLI](https://buildpacks.io/docs/install-pack/) from the Cloud Native Buildpacks project
* [Knative Serving](https://knative.dev/docs/install/any-kubernetes-cluster/#installing-the-serving-component) installed with the networking layer for Contour
* [Knative Eventing](https://knative.dev/docs/install/any-kubernetes-cluster/#installing-the-eventing-component) installed

> NOTE: This POC has only been tested on macOS running in a Bash shell.

## Features

The `springfun` command can initialize Spring Boot based function app that will run on Knative serving. You can add new functions using the `add` command. It also integrates with Knative Eventing via an `--ce-type` option and can initialize functions that can handle CloudEvents via a Trigger. It uses the CloudEvents Java SDK for managing the CloudEvents. The Knative EventType resource must specify a JSON schema that will be used for generating a Java class for the CloudEvent payload. 

Here is a `springfun` command example:

```
springfun init test
springfun add test --function event --ce-type com.example.testevent
```

Available commands are listed via the help text:

```
$ springfun --help
springfun is for Spring Functions on Knative
version 0.0.1

Commands:
  init         Initialize a function project
  add          Add a function to the project
  build        Build a function project container
  run          Run a function project container
  delete       Delete a function project container
```

## Installation

copy the CLI script to your PATH:

```
sudo curl https://raw.githubusercontent.com/trisberg/springfun/master/springfun \
 -o /usr/local/bin/springfun && \
sudo chmod +x /usr/local/bin/springfun
```

## Configure Skaffold

> The `default-repo` should match your Docker ID

Set the default repo for Skaffold:

```
skaffold config set default-repo $USER
```

## Simple upper function

### Initialize a new function

We'll create a function called `upper`:

```
springfun init upper --function upper
```

### Write the function code

Open in your favorite IDE (using Visual Studio Code in this example):

```
code upper/ upper/src/main/java/com/example/upper/UpperApplication.java
```

Modify the function bean to be:

```java
	@Bean
	public Function<String, String> fun() {
		return (in) -> {
			return in.toUpperCase();
		};
	}
```

### Run the function

We can now run the function using:

```
springfun run upper
```

### Invoke the function

Look up ingress:

```
INGRESS=$(kubectl get --namespace contour-external service/envoy -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
```

Invoke function with some data:

```
curl $INGRESS -H "Host: upper.default.example.com" -H "Content-Type: text/plain" -d hello && echo
```

### Delete the function

```
springfun delete upper
```

## A spring-demo function app that processes CloudEvents

This function app will have two functions, each with its own trigger for a specific CLoudEvent. One function accepts a SpringEvent event for a Spring release with version and date. This function generates a SpringNews event with a publishing date and a headline and some copy that gets returned as the invocation result. The new SpringNews event will then trigger the invocation of the other function that simply logs the news event.

### Configure eventing broker

Add a label to the namespace to have the eventing default broker start up:

```
kubectl label namespace default knative-eventing-injection=enabled
```

Verify that the broker is running:

```
kubectl -n default get broker.eventing.knative.dev default
```

### Create the EventType resources

The `com.example.springevent` EventType is defined as this:

```yaml
apiVersion: eventing.knative.dev/v1beta1
kind: EventType
metadata:
  name: com.example.springevent
  namespace: default
spec:
  type: com.example.springevent
  source: https://spring.io/spring-event
  schema: https://raw.githubusercontent.com/trisberg/springfun/master/types/spring-event.json
  description: SpringEvent type
  broker: default
```

The referenced schema has the following content:

```JSON
{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "title": "SpringEvent",
    "description" : "This is the schema for the SpringEvent type.",
    "type": "object",
    "properties": {
        "releaseDate": {
            "type": "string",
            "format": "date-time"
        },
        "releaseName": {
            "type": "string"
        },
        "version": {
            "type": "string"
        }
    },
    "additionalProperties": false
}
```

We have similar resources for `SpringNews` type.

Apply the manifest for the `com.example.springevent` and `com.example.springnews` EventTypes:

```
kubectl apply -f https://raw.githubusercontent.com/trisberg/springfun/master/config/spring-event.yaml
kubectl apply -f https://raw.githubusercontent.com/trisberg/springfun/master/config/spring-news.yaml
```

### Initialize the new function

We'll pass in the name of the EventTypes when we init the function.
During initialization the JSON schemas will get downloaded and added to the function so the build can generate a Java classes for the types.

```
springfun init spring-demo
springfun add spring-demo --function event --ce-type com.example.springevent
springfun add spring-demo --function news --ce-type com.example.springnews
```

### Write the function code

Open in your favorite IDE (using Visual Studio Code in this example):

```
code spring-demo/ spring-demo/src/main/java/com/example/springdemo/SpringDemoApplication.java
```

Modify the `event` function bean to be:

```java
	@Bean
	public Function<Message<JsonNode>, Message<SpringNews>> event() {
		return (in) -> {
			CloudEvent<AttributesImpl, SpringEvent> cloudEvent = CloudEventMapper.convert(in, SpringEvent.class);
			String results = "EVENT: " + cloudEvent.getData();
			System.out.println(results);
			// create return CloudEvent
			SpringEvent event = cloudEvent.getData().get();
			Map<String, Object> headerMap = new HashMap<>();
			headerMap.put("ce-specversion", "1.0");
			headerMap.put("ce-type", "com.example.springnews");
			headerMap.put("ce-source", "spring.io/spring-news");
			headerMap.put("ce-id", cloudEvent.getAttributes().getId());
			MessageHeaders headers = new MessageHeaders(headerMap);
			SpringNews news = new SpringNews();
			news.setWhen(new Date());
			news.setHeadline(event.getReleaseName() + " " + event.getVersion() + " Released");
			Locale loc = new Locale("en", "US");
			DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, loc);
			String releaseDate = dateFormat.format(event.getReleaseDate());
			String copy = event.getReleaseName() + " version " + event.getVersion() + " was released on " + releaseDate;
			news.setCopy(copy);
			return MessageBuilder.createMessage(news, headers);
		};
	}
```

Next, modify the `news` function bean to be:

```java
	@Bean
	public Function<Message<JsonNode>, String> news() {
		return (in) -> {
			CloudEvent<AttributesImpl, SpringNews> cloudEvent = CloudEventMapper.convert(in, SpringNews.class);
			String results = "NEWS: " + cloudEvent.getData();
			System.out.println(results);
			return "OK";
		};
	}
```

After resolving the imports the class should look like this:

```java
package com.example.springdemo;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.example.types.SpringEvent;
import com.example.types.SpringNews;
import com.fasterxml.jackson.databind.JsonNode;
import com.springdeveloper.support.cloudevents.CloudEventMapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import io.cloudevents.CloudEvent;
import io.cloudevents.v03.AttributesImpl;

@SpringBootApplication
public class SpringDemoApplication {

	@Bean
	public Function<Message<JsonNode>, Message<SpringNews>> event() {
		return (in) -> {
			CloudEvent<AttributesImpl, SpringEvent> cloudEvent = CloudEventMapper.convert(in, SpringEvent.class);
			String results = "EVENT: " + cloudEvent.getData();
			System.out.println(results);
			// create return CloudEvent
			SpringEvent event = cloudEvent.getData().get();
			Map<String, Object> headerMap = new HashMap<>();
			headerMap.put("ce-specversion", "1.0");
			headerMap.put("ce-type", "com.example.springnews");
			headerMap.put("ce-source", "spring.io/spring-news");
			headerMap.put("ce-id", cloudEvent.getAttributes().getId());
			MessageHeaders headers = new MessageHeaders(headerMap);
			SpringNews news = new SpringNews();
			news.setWhen(new Date());
			news.setHeadline(event.getReleaseName() + " " + event.getVersion() + " Released");
			Locale loc = new Locale("en", "US");
			DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, loc);
			String releaseDate = dateFormat.format(event.getReleaseDate());
			String copy = event.getReleaseName() + " version " + event.getVersion() + " was released on " + releaseDate;
			news.setCopy(copy);
			return MessageBuilder.createMessage(news, headers);
		};
	}

	@Bean
	public Function<Message<JsonNode>, String> news() {
		return (in) -> {
			CloudEvent<AttributesImpl, SpringNews> cloudEvent = CloudEventMapper.convert(in, SpringNews.class);
			String results = "NEWS: " + cloudEvent.getData();
			System.out.println(results);
			return "OK";
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringDemoApplication.class, args);
	}

}
```

### Run the function

We can now run the function using:

```
springfun run spring-demo
```

### Invoke the function directly

Look up ingress:

```
INGRESS=$(kubectl get --namespace contour-external service/envoy -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
```

Invoke function with some data:

```
curl ${INGRESS}/event -v -w'\n' \
 -H "Host: spring-demo.default.example.com" \
 -H "Ce-Specversion: 1.0" \
 -H "Ce-Type: com.example.springevent" \
 -H "Ce-Source: spring.io/spring-event" \
 -H "Content-Type: application/json" \
 -H "Ce-Id: 0001" \
 -d '{"releaseDate":"2004-03-24", "releaseName":"Spring Framework", "version":"1.0"}'
```

Check the logs:

```
kubectl logs -c user-container -l serving.knative.dev/configuration=spring-demo
```

### Post to the default broker

Start a `curl` pod:

```
kubectl run curl --generator=run-pod/v1 --image=radial/busyboxplus:curl -i --tty --rm
```

Post a few events:

```
curl -v "http://default-broker.default.svc.cluster.local" \
 -H "Ce-Specversion: 1.0" \
 -H "Ce-Type: com.example.springevent" \
 -H "Ce-Source: spring.io/spring-event" \
 -H "Content-Type: application/json" \
 -H "Ce-Id: 0001" \
 -d '{"releaseDate":"2004-03-24", "releaseName":"Spring Framework", "version":"1.0"}'
```

```
curl -v "http://default-broker.default.svc.cluster.local" \
 -H "Ce-Specversion: 1.0" \
 -H "Ce-Type: com.example.springevent" \
 -H "Ce-Source: spring.io/spring-event" \
 -H "Content-Type: application/json" \
 -H "Ce-Id: 0007" \
 -d '{"releaseDate":"2017-09-28", "releaseName":"Spring Framework", "version":"5.0"}'
```

```
curl -v "http://default-broker.default.svc.cluster.local" \
 -H "Ce-Specversion: 1.0" \
 -H "Ce-Type: com.example.springevent" \
 -H "Ce-Source: spring.io/spring-event" \
 -H "Content-Type: application/json" \
 -H "Ce-Id: 0008" \
 -d '{"releaseDate":"2018-03-01", "releaseName":"Spring Boot", "version":"2.0"}'
```

Check the logs in a separate terminal window:

```
kubectl logs -c user-container -l serving.knative.dev/configuration=spring-demo
```

You should see log messages similar to the following (ordering might be a bit different):

```text
2020-03-23 16:21:26.226  INFO 1 --- [           main] c.e.springdemo.SpringDemoApplication     : Started SpringDemoApplication in 4.81 seconds (JVM running for 5.941)
2020-03-23 16:21:26.751  INFO 1 --- [or-http-epoll-1] c.f.c.c.BeanFactoryAwareFunctionRegistry : Looking up function 'event' with acceptedOutputTypes: []
EVENT: Optional[com.example.types.SpringEvent@69b96b3b[releaseDate=Wed Mar 24 00:00:00 GMT 2004,releaseName=Spring Framework,version=1.0]]
2020-03-23 16:21:26.750  INFO 1 --- [or-http-epoll-4] c.f.c.c.BeanFactoryAwareFunctionRegistry : Looking up function 'event' with acceptedOutputTypes: []
EVENT: Optional[com.example.types.SpringEvent@2d64357e[releaseDate=Thu Sep 28 00:00:00 GMT 2017,releaseName=Spring Framework,version=5.0]]
2020-03-23 16:21:27.533  INFO 1 --- [or-http-epoll-4] c.f.c.c.BeanFactoryAwareFunctionRegistry : Looking up function 'news' with acceptedOutputTypes: []
2020-03-23 16:21:27.536  INFO 1 --- [or-http-epoll-1] c.f.c.c.BeanFactoryAwareFunctionRegistry : Looking up function 'news' with acceptedOutputTypes: []
NEWS: Optional[com.example.types.SpringNews@52d5f72c[when=Mon Mar 23 16:21:27 GMT 2020,headline=Spring Framework 1.0 Released,copy=Spring Framework version 1.0 was released on Mar 24, 2004]]
NEWS: Optional[com.example.types.SpringNews@34001f55[when=Mon Mar 23 16:21:27 GMT 2020,headline=Spring Framework 5.0 Released,copy=Spring Framework version 5.0 was released on Sep 28, 2017]]
2020-03-23 16:21:30.068  INFO 1 --- [or-http-epoll-4] c.f.c.c.BeanFactoryAwareFunctionRegistry : Looking up function 'event' with acceptedOutputTypes: []
EVENT: Optional[com.example.types.SpringEvent@1c0bf3f1[releaseDate=Thu Mar 01 00:00:00 GMT 2018,releaseName=Spring Boot,version=2.0]]
2020-03-23 16:21:30.084  INFO 1 --- [or-http-epoll-4] c.f.c.c.BeanFactoryAwareFunctionRegistry : Looking up function 'news' with acceptedOutputTypes: []
NEWS: Optional[com.example.types.SpringNews@3374b44d[when=Mon Mar 23 16:21:30 GMT 2020,headline=Spring Boot 2.0 Released,copy=Spring Boot version 2.0 was released on Mar 1, 2018]]
```

### Cleanup

Delete the Knative service and triggers:

```sh
springfun delete spring-demo
```

Delete the default broker:

```sh
kubectl label namespace default knative-eventing-injection-
kubectl delete broker.eventing.knative.dev/default
```
