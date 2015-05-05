# README

Salesforce.com fork of Cloudera's Distribution of Hadoop. 

## Build 

Builds can be found here: http://shared-bdabuild1-1-sfm.ops.sfdc.net:8080/job/hadoop2.x/

### Building Locally

You can build the full tarball locally, but you cannot deploy the tarball - only the build machine has privledges to do that. Therefore, the full build command is slightly different than the one used by the build machine.

However, before we can build locally, there are some dependencies you need to have installed on your machine.
	* Currently, the build only works on a GSE box - OSX is not yet supported.

#### Dependencies

 * gcc
   * $ sudo apt-get install g++

 * zlib
   * $ wget http://zlib.net/zlib-1.2.8.tar.gz; tar xzf zlib-1.2.8.tar.gz; cd zlib-1.2.8.tar.gz; ./configure; make test; sudo make install
   * Downloads and installs the latest verion of zlib 

 * protobuf 2.5.0
   * Step 1. Download source code :
   * https://code.google.com/p/protobuf/downloads/detail?name=protobuf-2.5.0.tar.bz2
   * Step 2. Extract :
   * $> tar -xvf protobuf-2.5.0.tar.bz2
   * Step 3. Build and install:
   * $> cd protobuf-2.5.0
   * $> ./configure
   * $> make
   * $> sudo make install


#### Simple build
You can do a simple build, which just compiles and jars up the sources via:

	$ mvn clean install -DskipTests

Builds and installs jars in the local maven repository, skipping tests. Its required to build/install the jars because of cross-module dependencies and maven not realizing that it needs to build the jars for dependent projects.

In following builds, where the jars are already installed, you can replace 'install' with 'compile'. However, this is advanced maven fu, so its not recommended unless you know what you are doing.

#### Complete Build

This is the complete build, as it as run on the server, minus deploying the finished jars to nexus

	$ mvn clean install -Pdist,native,docs,src -Dtar -Dbundle.snappy -Dsnappy.lib=/usr/local/lib

Note that it may not work as some tests may fail.

#### Tomcat source

Currently, [hadoop-hdfs-httpfs](src/hadoop-hdfs-project/hadoop-hdfs-httpfs) downloads tomcat-6.0.37.tar.gz from the cloudera website when building - this works when you have internet. However, there are cases when we don't (like on our build machine).

##### Specifying the Tomcat download location

You have the option to let it build that way or to specify the location from which the tarball can be obtain via the parameter

 > -Dtomcat.download.url

This is the location(http://archive.apache.org/dist/tomcat/tomcat-6/v6.0.37/bin/apache-tomcat-6.0.37.tar.gz) from which tomcat-6.0.37.tar.gz can be downloaded. For instance on my local machine I would specify:

 > -Dtomcat.download.url=file:///home/mbenioff/downloads/tomcat/tomcat-6.0.37.tar.gz

This is also how we can build a release of hadoop on the build machine, which has no general internet access.

## Releasing

You should use the release plugin on the jenkins patch job to do a release of Hadoop. Its already pre-configured with everything necessary to do a release.

## Eclipse

The full project can be imported into Eclipse fairly easily using the m2e plugin (most modern versions of Eclipse have this installed by default). 

First, make sure you have done at least a 'simple' local build. This primes the generated code so we can import into Eclipse properly.

Then, open Eclipse, and go to File -> Import -> Maven -> Existing Maven Projects and select the 'src' folder. Eclipse will automatically find all the projects. Hopefully, there should be no plugin errors - if there are, you can just ignore them for now.
*Known Issues:*
 * hadoop-distcp: goals 'copy-dependencies' and 'unpack' aren't supported by m2e
  * just 'Delete' this error - we aren't doing the real build in Eclipse, so we don't need to support all the targets

After Eclipse finishes importing the projects, hopefully everything should just build fine. However, sometimes it misses some of the generated source directories. To fix this, in the projects with errors look for a target/generated-sources folder; there will be either an avro or protobuf folder there. Right-click and select Build Path -> Use as Source folder. There should only be a handful of places where this is necessary - Eclipse is pretty good about auto-discovering generated source.

From here, you should be good to go! 

*NOTE:* You should always go back to the command line (maven) as the source of truth for building. Importing the project into eclipse is just a coding convenience.

## Updating versions

Use the [replace_version.sh](https://git.soma.salesforce.com/hbase/sfdc-hadoop-2.x-build/blob/master/replace-version.sh) script to update the version everywhere in the poms. This is preferable to using the [maven versions plugin](http://mojo.codehaus.org/versions-maven-plugin/set-mojo.html) as it will replace all instances of the version string, rather than just changing the versions in all the poms. This is necessary because hadoop isn't always consistent with using good maven practices and limits the breadth of changes in our fork. 
