---
layout: doc-page
title: Modernization Suite Set-Up
description: Ready to start configuring the tools and install the suite.
order: 3
image: set-up.svg
---

## Tool Installation

The AEM Modernization Tool suite is released as a standard AEM content package. It can be downloaded from the <a href="https://github.com/adobe/aem-modernize-tools/releases/" target="_blank">GitHub Release page</a> or included as a sub-package dependency of a configuration project.

The following steps assume you are adding this project to one created from AEM Archetype 21 or above.

### Step 1: Add Dependency

Note that all `<dependency>` entries listed below can be defined at the Reactor pom.xml with the version, type and classifier, and the version-less/type-less/classifier-less dependencies can be used in the sub-project poms. The instructions below define the dependencies directly in each sub-project pom for clarity and succinctness.

Add the following to the `<dependencies>` section of the _all project's pom.xml_ file:

{% highlight xml %}
<dependency>
    <groupId>com.adobe.aem</groupId>
    <artifactId>aem-modernize-tools.all</artifactId>
    <version>2.2.0</version>
    <type>zip</type>
    <!-- <classifier>aem65</classifier> optional, see below -->
</dependency>
{% endhighlight %}

#### Classifiers

The AEM Modernize Tools has multiple distributions; the following are available through classifiers:

* No classifier
  * AEM Version: Cloud Service
  * JDK Target: Java 11

* `aem65`
  * AEM Version: AEM 6.5.x
  * JDK Target: Java 11

* `java8aem65`
  * AEM Version: AEM 6.5.x
  * JDK Target: Java 8

### Step 2: Add as an Embed/Sub package

For more information on the Maven Project structural changes in Maven Archetype 21, please review [Understand the Structure of a Project Content Package in AEM as a Cloud Service](https://docs.adobe.com/content/help/en/experience-manager-cloud-service/implementing/developing/aem-project-content-package-structure.html). Note that this project structure is compatible with AEM 6.x as well.

In the `filevault-package-maven-plugin` configuration of your all project’s pom.xml file, add this:

{% highlight xml %}
<plugins> 
  <plugin>
    <groupId>org.apache.jackrabbit</groupId>
    <artifactId>filevault-package-maven-plugin</artifactId>
    ...
    <configuration>
      <embeddeds>
        <embedded>
          <groupId>com.adobe.aem</groupId>
          <artifactId>aem-modernize-tools.all</artifactId>
          <type>zip</type>
          <!-- <classifier>aem65</classifier> optional, see above -->
          <target>/apps/my-app-packages/application/install</target>
        </embedded>
      <embedded>
      ...
  </plugin>
</plugins>
{% endhighlight %}

### Step 3: Add Bundle as a Dependency (Optional)

If you plan to create custom implementations of the `RewriteRule` interface, your bundle project will need to add a dependency to the bundle artifact of this project. This can be done by adding this to your dependency list:

{% highlight xml %}
<dependency>
  <groupId>com.adobe.aem</groupId>
  <artifactId>aem-modernize-tools.core</artifactId>
  <scope>provided</scope>
</dependency>
{% endhighlight %}
