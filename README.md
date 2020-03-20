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

## Features

The `springfun` command can initialize Spring Boot based functions that will run on Knative serving. It also integrates with Knative Eventing and can initialize functions that can handle CloudEvents via a Trigger. It uses the CloudEvents Java SDK for managing the CloudEvents. If the Knative EventType resource specifies a JSON schema it will be used for generating a Java class for the CloudEvent payload.

## Installation

copy the CLI script to your PATH:

```
curl https://raw.githubusercontent.com/trisberg/springfun/master/springfun -o /usr/local/bin/springfun
chmod +x /usr/local/bin/springfun
```

## Simple upper Function

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

```
curl $INGRESS -H "Host: upper.default.example.com" -H "Content-Type: text/plain" -d hello && echo
```

### Delete the function

```
springfun delete upper
```
