## Framework supported Configuration

Here is the list of all supported configurations (for now):

## Nubes configuration

### `src-package`


* **Type:**   *String*

* **Description:**   This is the base package of your project, it should look like `user.company.projectname`. It's a convention-over-configuraton shorthand for other keys below. If you follow our own conventions, you'll only need this key set-up. If not, the keys below will override the default ones.

* **Default:** No default value (then you'll need to define controllers, fixtures, domains, verticle, services packages independently)


### `controller-packages`


* **Type:**   *JsonArray*

* **Description:**   This is a JsonArray, which should contain all the packages where your controllers are located. 

* **Default:**	 If the key is missing, the value will be a concatenation of the `src-package` value with `.controllers`. If it doesn't exist, well, you'll have no controllers set-up. Which can be fine if you're not using them.


### `fixture-packages`


* **Type:**   *JsonArray*

* **Description:**   This is a JsonArray, which should contain all the packages where your fixtures are located. 

* **Default:**  `src-package` value concat with `.fixtures`.


### `domain-package`


* **Type:**   *String*

* **Description:**   This will set the package of your domains.

* **Default:**	  `src-package` value concat with `.domains`.


### `verticle-package`


* **Type:**   *String*

* **Description:**   This will set the package of your verticles.

* **Default:**	  `src-package` value concat with `.verticles`.


### `services`


* **Type:**   *JSonObject*

* **Description:**  This is a JsonObject which contains all the services to be registered by Nubes. The key is the service name (String), and the value is the service class name (also a String).

* **Default:**   No default value.


### `templates`


* **Type:**   *JsonArray*

* **Description:**   A JsonArray (of String) which contains all templates supported by the application. The expected values are: `hbs` for Handlebars, `jade` for Jade, `templ` for MVEL and `thymeleaf` for Thymeleaf (for the last one, you need to use .html file extension).

* **Default:**   No default value.


### `throttling`


TODO : 

* count
* time-frame
* time-unit


### `webroot`


* **Type:**   *String*

* **Description:**   This will set the webroot for your application.

* **Default:**	  default value is `web/assets`.


### `static-path`


* **Type:**   *String*

* **Description:**   This will set the path for your static files in your application.

* **Default:**	  default value is `/assets`.
* 
### `views-dir`


* **Type:**   *String*

* **Description:**   This will set the path for your template files in your application.

* **Default:**	  default value is `web/views`.


### `Configuration example`

Here is an example of a valid `conf.json` file :

```json
{
	"host":"localhost",
	"port":8080,
  	"src-package":"mycompany.myproject",
  	"domain-package":"i.put.my.domains.where.i.want.to",
  	"fixture-packages":["fixtures.are.not.in.convention.package"],
  	"controller-packages":["definitely.not.using.your.conventions"],
  	"services":{"taskService":"mycompany.myproject.services.TaskService"},
  	"templates":["hbs"]

}
```

As you can see, some values are overriden and aren't using Nubes conventions: 
* domains
* fixtures
* controllers


It's not the case of the `verticle-package` key, which is missing from the configuration. 
Thus the value in the configuration will then be `mycompany.myproject.verticles`. If the package exists and contains verticles (annotated with `@Verticle`, Nubes will deploy them. If it's empty or does'nt exist, well... Nubes won't...


## NubesServer configuration

Sometimes you'll just don't need to instantiate Nubes programmatically and just deploy a web-server with everything already set-up.

In this case, you'll use `NubesServer` our own implementation of a web-server Verticle, and you can specify two more options in your configuration :

### `host`

* **Type:**   *String*

* **Description:**   This is the hostname of your server.

* **Default:**   Default value (only for NubesServer instance) is `localhost`.

### `port`

* **Type:**   *int*

* **Description:**   This is the port of your server.

* **Default:**   Default value (only for NubesServer instance) is 9000.
