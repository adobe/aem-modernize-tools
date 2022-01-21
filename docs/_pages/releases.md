---
layout: content-page
title: Releases
---

The AEM Modernization Tool project keeps a changelog of significant changes to the project, by version:

<a href="https://github.com/adobe/aem-modernize-tools/blob/main/CHANGELOG.md" class="button">View the full changelog</a>

<ul>
{{ site.github.repository_url }}

{% for release in site.github.releases %}
  <li><a href="{{ release.html_url }}">{{ release.name }}</a></li>
{% endfor %}


</ul>

