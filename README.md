# Dynamo
This folder contains the project files for the Distributed Systems course, 2019/2020 Q3 edition.

This project was done by group 17. The team members are:
- Jim Verheijde
- Gerben Oolbekkink
- Stas Mironov
- Stefan van der Heijden

The paper that was implemented and reproduced is Dynamo, which was originally developed by Amazon. The original paper introducing Dynamo can be found here: https://dl.acm.org/doi/10.1145/1294261.1294281

## Requirements
This project is structured as an sbt project. Therefore you need both `Scala` and `sbt` installed.
This project has been tested to work with Scala 2.13.1 as defined in the `build.sbt` file. 

## Run instructions

### Cluster
There are several ways to run a Dynamo cluster. 

**Simple local cluster**

If you simply want to run a local cluster, run the `mainObj` file using:
 
```sbt "runMain dynamodb.node.mainObj"```

**Cluster on different nodes**

If you want to run a cluster on multiple nodes use the files in the `dynamodb.cluster` package. 
There is a 3 node cluster and 7 node cluster preconfigured.
The configuration is stored in the `package` file in each of these clusters. Change these values to your liking.
To run on different nodes you will need to set `local` to `false` in `dynamodb.cluster.clusterx.package`, 
otherwise the cluster will only run locally for debugging purposes.
To run the 7 node cluster run:

```sbt "runMain dynamodb.cluster.cluster7.node1```

```sbt "runMain dynamodb.cluster.cluster7.node2```

...

```sbt "runMain dynamodb.cluster.cluster7.node7```
on the different nodes, make sure the IP addresses of these nodes are updated in the `dynamodb.cluster.cluster7.package` file.

If you want to make a custom cluster create a new package `cluster{x}` and copy the files from either `cluster3` or `cluster7` and change the values.

### Client
We also implemented a simple client that can send queries to the cluster. This is located in the `dynamodb.client` package.


## Tests
Note that some end to end tests will fail while a cluster is already running, so make sure that when running tests locally all clusters are terminated.

To run the test suite run:

```sbt "testOnly dynamodb.node.*"```

The reason to use this command instead of `sbt test` is that we also have a very long benchmark spec defined in the test folder which can take around 10 minutes.