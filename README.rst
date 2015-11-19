========
sbt-avro
========

Overview
========
This is a fork of https://github.com/cavorite/sbt-avro and https://github.com/ch4mpy/sbt-avro

sbt-avro is a plugin for sbt-0.13.0 for generating the Java
sources for Avro_ schemas and protocols.

.. _Avro: http://avro.apache.org/

Usage
=====

Install the plugin
------------------

Add the plugin according to the `sbt documentation`_.

.. _`sbt documentation`: http://www.scala-sbt.org/0.13/docs/Plugins.html

For instance, add the following lines to the file ``project/plugins.sbt`` in your
project directory::


    addSbtPlugin("me.andreionut" % "sbt-avro" % "1.0.1")


Import the plugin settings
--------------------------

To activate the plugin, import its settings by adding the following lines to 
your ``build.sbt`` file::

    seq( me.andreionut.SbtAvro.avroSettings : _*)


Scope
=====
All settings and tasks are in the ``avro`` scope. If you want to execute the
``generate`` task directly, just run ``avro:generate``.


Settings
========

===============     ====================     ================================     ===============
Name                Name in shell            Default                              Description
===============     ====================     ================================     ===============
sourceDirectory     ``source-directory``     ``src/main/avro``                    Path containing ``*.avsc``, ``*.avdl`` and ``*.avpr`` files.
javaSource          ``java-source``          ``$sourceManaged/compiled_avro``     Path for the generated ``*.java`` files. See Known Bugs section.
version             ``version``              ``1.7.3``                            Version of the Avro library should be used. A dependency to ``"org.apache.avro" % "avro-compiler" % "$version"`` is automatically added to ``libraryDependencies``.
stringType          ``string-type``          ``CharSequence``                     Java type for string elements. Possible values: ``CharSequence`` (by default), ``Utf8`` and ``String``.
===============     ====================     ================================     ===============

Example
-------

For example, if you want to change the Java type of the string elements in 
the schema, you can add the following lines to your ``build.sbt``  file: 
    
    seq( me.andreionut.sbtavro.SbtAvro.avroSettings : _*)
    
    (stringType in avroConfig) := "String"


Tasks
=====

===============     ================    ==================
Name                Name in shell        Description
===============     ================    ==================
generate            generate            Compiles the Avro files. This task is automatically executed everytime the project is compiled.
===============     ================    ==================

Known Bugs
==========

- On ``sbt clean`` the directory set by the ``javaSource`` setting will be erased.

License
=======
This program is distributed under the BSD license. See the file ``LICENSE`` for
more details.

Credits
=======

`sbt-avro` was created by `Juan Manuel Caicedo`__.

Contributors
------------

- `Brennan Saeta`_
- `Daniel Lundin`_
- `Vince Tse`_
- `Ashwanth Kumar`_
- `Jerome Wacongne`_

.. _`sbt-protobuf`: https://github.com/gseitz/sbt-protobuf
.. _`Brennan Saeta`: https://github.com/saeta
.. _`Daniel Lundin`: https://github.com/dln
.. _`Vince Tse`: https://github.com/vtonehundred
.. _`Ashwanth Kumar`: https://github.com/ashwanthkumar
.. _`Jerome Wacongne`: https://github.com/ch4mpy
.. __: http://cavorite.com


