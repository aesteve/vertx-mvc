## Using websockets with Nubes

Like Vertx-Web, Nubes allows verticles to communicate through the event bus.
We will see here how a (javascript) client can communicate with a Nubes server using websockets.



### The EventBus Controller : @EventBusBridge

To allow communication between a client and a Nubes server, we need to build a bridge, using the `@EventBusBridge` annotation :


```java

@EventBusBridge("/eventbus/*")
public class EBBridgeController {
}

```

Now that we have created our bridge, the client and server are able to communicate through the bus, if they use the same address.                                                                          
However, for security reasons, it is highly recommended to limit the allowed addresses to those you are actually using! 


To do so, you can use `@OutboundPermitted` and `@InboundPermitted` annotations :


```java

@EventBusBridge("/eventbus/*")
@OutboundPermitted(address = "chat.to.client")
@InboundPermitted(address = "chat.to.server")
public class EBBridgeController {
}


```

It works just like with Vertx-Web, your clients can now only send messages at `chat.to.server` address, and listen at `chat.to.client`. 

Nubes also supports regex for those permitted options :



```java

@EventBusBridge("/eventbus/*")
@OutboundPermitted(addressRegex = "chat.to.*")
@InboundPermitted(addressRegex = "chat.to.*")
public class EBBridgeController {
}

```



Like Vertx-Web, Nubes allows you to set as much addresses as you like :



```java

@EventBusBridge("/eventbus/*")
@OutboundPermitted(addressRegex = "an.outbound.address")
@OutboundPermitted(addressRegex = "another.outbound.address")
@InboundPermitted(addressRegex = "an.inbound.address*")
@InboundPermitted(address = "another.inbound.address")
@InboundPermitted(addressRegex = "another.other.inbound.address")
public class EBBridgeController {
}

```

Those permitted options also allow you to set required authorities.
For example, if you have a database and you just want some users (let's say admin ones) to add/delete datas. 
You can still use the EventBus, you just have to add required authorities to addresses when it is needed :

```java
@EventBusBridge("/eventbus/*")
@OutboundPermitted(address = "database.datas")
@InboundPermitted(address = "database.add", requiredAuthority = "admin")
@InboundPermitted(address = "database.delete", requiredAuthority = "admin")
@InboundPermitted(address = "database.get.all")
public class EBBridgeController {

}
```

In this case, only users with the `admin` authority can register `database.add` and `database.delete` addresses. 
This way, you can protect your bus against attacks, by requiring authentication to communicate.


Now you know everything you need to create your own bridge!


### The @Consumer annotation in Services

Once you've created your brigde (let's say the `chat.to.server` one) you can create a Service to handle the communications on the Nubes server side, using the `@Consumer` annotation:

```java

public class MessageService implements Service {

    private Vertx vertx;

    [...]

    @Consumer("chat.to.server")
    public void messageHandler(Message<String> message) {
    
        vertx.eventBus().publish("chat.to.client", "message received from the server!");
    }
}

```

This basic Service implementation will listen on the EventBus at the address `chat.to.server`, and will publish the message `message received from the server!` when a message is received. 

You can set as much `@Consumer` addresses as you want in the same Service, which can be very useful sometimes (think of the database example).

This is basically all you need to know about services and`@Consumer` annotation, go write our own now :)

### The client side

This is exactly the same client you would create for a Vertx-Web application.

First, you need to import the `sockjs.min.js` and `vertxbus.js` files to your html page. 
Then you just have to create an EventBus instance, and you can register/send/publish to the chosen addresses on the bus.                                                                                                                
Here is a small example : 

```js

<html>
<head>
[...]
<script src="//cdn.jsdelivr.net/sockjs/0.3.4/sockjs.min.js"></script>
<script src="/assets/js/vertxbus.js"></script>
</head>

<body>
[...]
<script>
  var eb = new vertx.EventBus("/eventbus/");
  eb.onopen = function () {
    eb.registerHandler("chat.to.client", function (msg) {
      alert(msg);
    });
    eb.send("chat.to.server","test message");
  };

</script>
</body>

</html>

```

This client creates an EventBus, and register to the `chat.to.client` address. Every message received at this address will pop-up in an alert box. 
It will also send a message to the address `chat.to.server`, which will be received by the MessageService create before, thanks to the magic bridge!

I believe you know everything you need to build your own Nubes web application using the EventBus!

For a complete example, you can go [here](https://github.com/ldallen/Nubes-UseCases).
