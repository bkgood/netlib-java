netlib-java [![Build Status](https://travis-ci.org/fommil/netlib-java.png?branch=master)](https://travis-ci.org/fommil/netlib-java)
===========

Mission-critical software components for linear algebra systems.

[Netlib](http://netlib.org/liblist.html) is a collection of mission-critical software components for linear algebra systems (i.e. working with vectors or matrices). Netlib libraries are written in C, Fortran or optimised assembly code. A Java translation has been provided by the [F2J](http://sourceforge.net/projects/f2j/) project but it does not take advantage of optimised system libraries.

The [Matrix Toolkits for Java](https://github.com/fommil/matrix-toolkits-java) project provides a higher level API and is recommended for programmers who do not specifically require a low level Netlib API.

This project provides a wrapper layer that gives Java programmers access to a common API which can be configured to use either the pure Java or natively optimised implementations of BLAS, LAPACK and ARPACK such as

  * Intel's [Math Kernel Library](http://www.intel.com/cd/software/products/asmo-na/eng/307757.htm)
  * AMD's [Core Math Libary](http://developer.amd.com/acml.jsp)
  * Apple's [vecLib Framework](http://developer.apple.com/hardwaredrivers/ve/vector_libraries.html)
  * the popular open source [ATLAS](http://math-atlas.sourceforge.net).

This ensures perfect portability, while allowing for improved performance in a production environment. Users are advised to perform the appropriate performance profiling with Java and native implementations... the pure Java implementation is surprisingly fast. A good rule of thumb is that native libs only out-perform the Java implementation if specific hardware is used or matrices are larger than 1000 x 1000 elements.

If you wish to use the JNI, then you may find the necessary source files in the folder `jni`. You should be able to run the script `configure` and then `make` to produce the necessary JNI library files for your system. You will need to have the headers for BLAS and LAPACK on your system, a C compiler and a Fortran compiler (in order to build ARPACK).

Most of the code in netlib-java is autogenerated. If you wish to regenerate the files yourself from the source, you will need to grab the `jlapack-0.8-javadoc.zip` javadoc file from jlapack-0.8 and place it into the `lib/f2j` directory.


Donations
=========

Please consider supporting the maintenance of this open source project with a donation:

[![Donate via Paypal](https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=B2HW5ATB8C3QW&lc=GB&item_name=netlib-java&currency_code=GBP&bn=PP%2dDonationsBF%3abtn_donateCC_LG%2egif%3aNonHosted)


Contributing
============

Contributors are encouraged to fork this repository and issue pull
requests. Contributors implicitly agree to assign an unrestricted licence
to Sam Halliday, but retain the copyright of their code (this means
we both have the freedom to update the licence for those contributions).
