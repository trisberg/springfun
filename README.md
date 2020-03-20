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

The `springfun` command can initialize Spring Boot based functions that will run on Knative serving. It also integrates with Knative Eventing and can initialize functions that can handle CloudEvents via a Trigger. It uses the CloudEvents Java SDK for managing the CloudEvents. If the Knative EventType resource specifies a JSON schema it will be used for generating a Java class for the CloudEvent payload.

```
$ springfun --help
springfun is for Spring Functions on Knative
version 0.0.1

Commands:
  init         Initialize a function
  build        Build a function container
  run          Run a function container
  delete       Delete a function container
```

## Installation

copy the CLI script to your PATH:

```
curl https://raw.githubusercontent.com/trisberg/springfun/master/springfun -o /usr/local/bin/springfun
chmod +x /usr/local/bin/springfun
```

## Simple upper function

### Initialize a new function

We'll create a function called `upper`:

```
springfun init upper
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

## A spring-event function that processes CloudEvents

Add a label to the namespace to have the eventing default broker start up:

```
kubectl label namespace default knative-eventing-injection=enabled
```

Verify that the broker is running:

```
kubectl -n default get broker.eventing.knative.dev default
```

### Create the EventType resource

The EventType is defined as this:

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

Apply the manifest for the EventType:

```
kubectl apply -f https://raw.githubusercontent.com/trisberg/springfun/master/config/spring-event.yaml
```

### Initialize the new function

We'll pass in the name of the EventType when we init the function.
During initialization the JSON schema will get downloaded and added to the function so the build can generate a Java class for the type.

```
springfun init spring-event --ce-type com.example.springevent
```

### Write the function code

Open in your favorite IDE (using Visual Studio Code in this example):

```
code spring-event/ spring-event/src/main/java/com/example/springevent/SpringEventApplication.java
```

Modify the function bean to be:

```java
	@Bean
	public Function<Message<JsonNode>, String> fun() {
		return (in) -> {
			CloudEvent<AttributesImpl, SpringEvent> cloudEvent = CloudEventMapper.convert(in, SpringEvent.class);
			String results = "Processed: " + cloudEvent.getData();
			System.out.println(results);
			return "OK";
		};
	}
```

After resolving the imports the class should look like this:

```java
package com.example.springevent;

import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.springdeveloper.support.cloudevents.CloudEventMapper;
import com.example.types.SpringEvent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

import io.cloudevents.CloudEvent;
import io.cloudevents.v03.AttributesImpl;

@SpringBootApplication
public class SpringEventApplication {

	@Bean
	public Function<Message<JsonNode>, String> fun() {
		return (in) -> {
			CloudEvent<AttributesImpl, SpringEvent> cloudEvent = CloudEventMapper.convert(in, SpringEvent.class);
			String results = "Processed: " + cloudEvent.getData();
			System.out.println(results);
			return "OK";
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(SpringEventApplication.class, args);
	}

}
```

### Run the function

We can now run the function using:

```
springfun run spring-event
```

### Invoke the function directly

Look up ingress:

```
INGRESS=$(kubectl get --namespace contour-external service/envoy -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
```

Invoke function with some data:

```
curl $INGRESS -v -w'\n' \
 -H "Host: spring-event.default.example.com" \
 -H "Ce-Specversion: 1.0" \
 -H "Ce-Type: com.example.springevent" \
 -H "Ce-Source: spring.io/spring-event" \
 -H "Content-Type: application/json" \
 -H "Ce-Id: 0001" \
 -d '{"releaseDate":"2004-03-24", "releaseName":"Spring Framework", "version":"1.0"}'
```

Check the logs:

```
kubectl logs -c user-container -l serving.knative.dev/configuration=spring-event
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
kubectl logs -c user-container -l serving.knative.dev/configuration=spring-event
```

### Delete the function

```
springfun delete spring-event
```
