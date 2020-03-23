# Spring-Demo for CloudEvents

## Prerequisits

See: https://github.com/trisberg/springfun#prerequisites

## Building

> The `--default-repo` should be your Docker ID

```sh
skaffold run --default-repo=$USER
```

## Testing

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

## Cleanup

```sh
springfun delete spring-event
```
