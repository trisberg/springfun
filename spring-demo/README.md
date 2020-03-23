# Spring-Demo for CloudEvents

## Prerequisits

See: https://github.com/trisberg/springfun#prerequisites

## Building

> The `--default-repo` should be your Docker ID

```sh
skaffold run --default-repo=$USER
```

## Testing

Add a label to the namespace to have the eventing default broker start up:

```
kubectl label namespace default knative-eventing-injection=enabled
```

Verify that the broker is running:

```
kubectl -n default get broker.eventing.knative.dev default
```

Post to the default broker

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
## Cleanup

Delete the Knative service and triggers:

```sh
skaffold delete
```

Delete the default broker:

```sh
kubectl label namespace default knative-eventing-injection-
kubectl delete broker.eventing.knative.dev/default
```
