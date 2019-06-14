# XTC Stats Application

![Screenshot](https://i.imgur.com/1KBbjTI.png)


This application can be used as both an explorer as well as a monitoring tool, to check for wrong timestamps in blocks, block sizes etc etc. It doesn't talk to the Torque daemon at all, it hooks itself directly onto the blockchain database.

## Prerequisites

*    Can run only on Unix/Linux systems with the 'ln' command and java8.

* Create a directory in the same partition as the blockchain folder (~/.torque) for example : `mkdir /home/hayzam/torque_clone && mkdir /home/hayzam/torque_clone/lmdb`

* Enter the lmdb directory directory  `cd /home/hayzam/torque_clone/lmdb`

* Make the hardlink (NOT a symlink) to the original file, `ln ~/.torque/lmdb/data.mdb data.mdb`

## Configuration

The application-prod.yaml at the project root directory must be modified

-   Choose the HTTP listening port, the host & the context path if required.
    
-   In section lmdb, put your torque home directory like this :
    
    `stelllite_home: /home/hayzam/torque_clone`
    
-   `env_path:lmdb_stats`  : The env_path is the name (relative path) of the new LMDB stats db.
    
-   `attack_delay_sec` : the delay for an attack in future (For trying another delay parameter, remove the stats db and restart the app!)
    
-   `attack_block_size_bytes` : The maximum size of a block to be considered a non-transaction including block.
    
-   `debug` : true to setup in debug mode.

## Build and launch with gradle

-   Build and launch without tests (`-x test` to exclude test):
    
    `clear && ./gradlew clean build -x test bootRun`
    
-   Launch with test
    
    `./gradlew clean build bootRun`


## Executing JAR with embedded Tomcat

```
$ cd build/libs
$ java -Dspring.profiles.active=prod -jar xtl-stats-0.1.0.jar
(will use application-prod.yaml)
```
For remote debugging use :

```
java -Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=8000,suspend=n -Dspring.profiles.active=dev -jar xtl-stats-0.1.0.jar
```

## USE REST SERVICES

Goto the default home page [http://localhost:8686/](http://localhost:8686/) and you will see the list of available services

## To-Do list

- [ ] Better front end.
- [ ] Bring download option in both CSV and JSON.
