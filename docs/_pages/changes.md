---
layout: section-page
title: Important Changes
subtitle: Details on the changes that have been introduced in the tool suite.
---

{% assign sorted = site.pages | sort: name %}
{% assign currentPagePath = page.path | remove: '.md' %}

{% for subpage in sorted %}
    {% capture isSubPath %}{% if subpage.path contains currentPagePath %}true{% else %}false{% endif %}{% endcapture %}
    {% capture isNotSame %}{% if subpage.path != page.path %}true{% else %}false{% endif %}{% endcapture %}
    {% capture subFeature %}{% if isSubPath == 'true' and isNotSame == 'true' %}true{% else %}false{% endif %}{% endcapture %}

    {% if subFeature == 'true' %}

        {% include change.html content=subpage.content title=subpage.title %}

    {% endif %}

{% endfor %}

