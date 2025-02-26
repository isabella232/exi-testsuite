
EXI Interoperability Framework
------------------------------

This distribution was developed by the Efficient XML Interchange (EXI) Working Group.  
It may be updated, replaced or obsoleted at any time. It is not intended as a means 
to validate conformance of EXI implementations, but rather to evaluate the clarity 
of the EXI Format 1.0 Specification. However, it may also be used to facilitate EXI 
implementation development. Comments/questions should be sent to the public-exi@w3.org 
mailing list (Archive is at http://lists.w3.org/Archives/Public/public-exi/). 


Introduction
------------

The EXI interoperability framework is a testing framework developed by the W3C
EXI working group for the purpose of conducting interoperability assessment.
It uses XML test files designed to cover features in the EXI spec. The XML
test data files are fed to EXI implementations to generate EXI encodings.  
The encodings are decoded to produce round-tripped XML files to be compared 
with the original XML test files. 

The EXI framework is built on top of Japex which provides basic functionality 
for drawing charts, generating XML and HTML reports, etc. Japex is included 
in this distribution.


Directory structure
-------------------

'candidates' directory contains sub-directories for each implementation. 
The implementation files and test drivers are located in these sub-directories.

'config' directory contains the configuration files for running the tests. 
You can find both drivers' configuration files and tests cases' configuration 
files in this directory.

'data' directory contains the XML test data files.

'encodings' directory contains the EXI encodings generated by several
implementations using this framework. Each set of EXI encodings can be
used for running decoding tests.

'framework' directory contains the framework that is the basis for running 
the tests. Each implementation extends a class in this framework to provide 
a test driver component.

'japex' directory contains the Japex distribution.


Setting Up Implementations for Testing
--------------------------------------

Not all EXI implementations are included in this distribution and must be 
downloaded and plugged into the framework to run the tests. To plug-in an 
implementation, you need to have both an EXI implementation and its corresponding 
drivers. The framework is defined to run even if all the implementations used 
in the EXI WG testing are not available on your system. Instructions for 
obtaining the EXI implementations used in EXI WG testing are given below.

  EXIficient - The implementation and a driver is included in this distribution.

  Canon's EXI Implementation - Please contact youenn.fablet@crf.canon.fr using 
                               "W3C EXI interop framework" in the subject line to 
                               request access to Canon's EXI implementation and 
                               the corresponding drivers.

  OpenEXI - The implementation and a driver is included in this distribution.

See http://www.w3.org/XML/EXI/#implementations for a current list of EXI implementations.

This distribution includes drivers for EXIficient and OpenEXI. These drivers use the 
SAX API to integrate the implementations with the framework. Writing drivers for other 
Java implementations should be straightforward using the provided drivers as templates.

To set up the implementation, it should be placed in 'candidates/<implementation>/lib/'.  
You will also need to update the following configuration files to trigger the use of the 
implementation:

* 'config/property/interoperability/encoding/java/drivers.xml' for encoding tests
* 'config/property/interoperability/decoding/java/drivers.xml' for decoding tests

Note that the framework distribution already contains a set of EXI encoded files for 
each of the three EXI implementations used by the EXI WG in interoperability testing: 
Exificient, Canon's EXI implementation and OpenEXI.


How to Use
----------

You must have Ant (http://ant.apache.org) to build and run the framework. 

Open a command line tool and go to the root of the EXI interoperability framework folder.

To run encoding tests, type:
	ant run-iot-encoding-classes-java -DtestCases=config/testCases-interop/all.xml

The last parameter config/testCases-interop/all.xml' may be replaced by any other test 
configuration file. Encoding results will be put in a sub-folder of the 
'reports/property/interoperability/encoding' folder.

To run decoding tests, type:
	ant run-iot-decoding-classes-java -DtestCases=config/testCases-interop/all.xml  -DexiDataDir=./encodings/Canon

The ' config/testCases-interop/all.xml' parameter may be replaced by any other test configuration file.

The './encodings/Canon' parameter may be replaced by any other encoding set.

Three full encoding sets are provided in the test framework within the 'encodings' folder.

Decoding results will be put in a sub-folder of the 'reports/property/interoperability/decoding' 
folder.


Limitations
-----------

This release of the framework supports Java-based EXI implementations only.

This release requires JDK 1.5.  It has not been tested with other Java versions.

The data-type aware method of comparing XML files (i.e., diff tool) has been maintained 
to avoid false difference reports when using the included test data files.  Other test 
data files may trigger false difference reports.

Depending on the local installation directory, path names used in this release may trigger
path-length errors in some operating systems.  Affected working group members resolved this
by using a simple work-around of setting up the testing in environment in a local directory 
closer to the root.  

