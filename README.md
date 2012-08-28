clact
=====

Your friends say funny things. This becomes your friend facts. We call it Clacts!

This repo supports [this blog post](http://paulosuzart.github.com/blog/2012/07/09/tcp-server-with-clojure-aleph-and-gloss/).

Usage
=====
Clone it. Uberja it. And then run the jar: `java -jar ${jar-versio-name}.jar`

```
   Switches               Default         Desc                                      
   --------               -------         ----                                     
   -p, --port             10200           Port to listen to connections             
   -s, --storage          sqlite          Type of storage. Use sqlite or datomic    
   -t, --transactor       localhost:4334  Transactor server:port. For datomic only. 
   -h, --no-help, --help  false           Show this help 
``` 

Then use `telnet localhost 1022` for example to start interacting with
it.

Use the following commands:

   * `PUT <author> <via> <fact>`: Insert a new fact
   * `LSA <autor>`: Find fact by a given author
   * `LSA *`: Find all facts regardless the author (Works only for
SQLite storage)
   * `LSC <content>`: Find facts by their content. (Works only for
datomic storage)
