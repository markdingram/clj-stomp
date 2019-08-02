

Before running tests start a Stomp Broker:

````
docker run -it --rm \
  --ulimit nofile=122880:122880 \
  -p 8161:8161 \
  -p 61613:61613 \
  -p 61616:61616 \
  vromero/activemq-artemis
````

The UI is on port 8161, login with: artemis / simetraehcapa

Run tests with `bin\koacha`.
