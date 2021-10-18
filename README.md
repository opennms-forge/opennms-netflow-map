# OpenNMS NetFlow Map

Small utility to display a wargame-like map visualizing the flows collected by OpenNMS.

In order to use the tool, download `GeoLite2-City.mmdb` from Maxmind and place it in the `src/main/resources directory`. After that you can build by entering:

    $ mvn install

and after that, execute the utility by entering:

    $ java -jar target/opennms-netflow-map-1.0-SNAPSHOT-jar-with-dependencies.jar 

Command line options:

     -h the ElasticSearch host to be used, default is localhost
     -p the ElasticSearch port to be used, default is 9200
     -a a public IP address to be used instead when resolving private IP addresses, default is 8.8.8.8

Easiest way is to ssh into your ElasticSearch box and forward traffic to the ElasticSearch port:

    ssh -L9200:localhost:9200 user@elastic.search.host