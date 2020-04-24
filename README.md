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
There are several ways to run a Dynamo cluster. 

**Simple local cluster**

If you simply want to run a local cluster, run the `mainObj` file using:
 
`sbt "runMain dynamodb.node.mainObj"`

If you want to run a 