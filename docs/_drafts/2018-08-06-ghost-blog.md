---
title: Ghost Theme and Site Development
canonical_url: https://blog.hcf.dev/article/2018-08-06-ghost-blog/
tags:
 - Ghost
 - blog
 - Bootstrap
 - jQuery
 - html
 - css
 - Prism
permalink: article/2018-08-06-ghost-blog
---

This is the inaugural post to this blog.  Specifically, this post documents
setting up this blog.

## Requirements

My requirements were:

1. Professional appearance and quality user experience
2. Deploy-able with [Ansible]
3. Minimize the application "native" data
    * Prefer configuration stored in "source" format within source control
4. Custom theme
    * Emphasis on code
5. Allow import of articles written external to the application (which will
   also be managed within source control)

After some investigation, I settled on [Ghost].  The next section details
the strategy for responding to these requirements.

## Strategy

[Ghost] now provides [Ghost CLI] which we'll ultimately combine with
[Ansible] for deployment.

The custom theme will integrate [Bootstrap] and [Prism] (for code
highlighting).

## Development Instance

The Ghost directions are straightforward.  Ghost is not supported with the
latest version of [node.js] so in a [macOS] environment managed with
[Homebrew] a supported version must be found on the path.

```bash
$ mkdir ghost
$ cd ghost
$ env PATH=$(brew --prefix node@8)/bin:$PATH ghost install local
$ env PATH=$(brew --prefix node@8)/bin:$PATH ghost restart --development
```

That wisdom is captured in the following `Makefile`.

```makefile
# ghost.mk

MAKEFILE=$(lastword $(MAKEFILE_LIST))
DIRECTORY=$(basename $(MAKEFILE))

export PATH:=$(shell brew --prefix node@8)/bin:${PATH}

$(DIRECTORY):
	mkdir $@
	cd $@; ghost install --local --no-start

start restart:	$(DIRECTORY)
	cd $<; ghost $@ --development

stop:	$(DIRECTORY)
	cd $<; ghost $@

# Local Variables:
# compile-command: "make -f ghost.mk "
# End:
```

The instance will be served locally @ <http://localhost:2368/> with the
administration interface @ <http://localhost:2368/ghost/>.  Upon initial
start-up, the administration screen will present a dialog for initial
configuration which should be completed.  In addition,
[general](http://localhost:2368/ghost/#/settings/general) settings should be
configured.

Also available on the [administration page](http://localhost:2368/ghost/#/)
menu is the [Labs](http://localhost:2368/ghost/#/settings/labs/) option
which provides two important features:

1. [Import content](http://localhost:2368/ghost/#/settings/labs)
2. [Delete all content](http://localhost:2368/ghost/#/settings/labs)

From the "ghost" directory, use:

```bash
$ env PATH=$(brew --prefix node@8)/bin:$PATH ghost stop
```

to stop the instance (or the corresponding "stop" target in `ghost.mk`).

## Creating the Theme

When the server is started with `--development`, theme content may be added
directory to `content/themes`.  For example, to start this blog's theme:

```bash
$ git clone https://github.com/TryGhost/Casper.git blog-ghost-theme
```

And then edit the `package.json` file within the directory updating the
name, description, and version fields.  Restart the server so the newly
created theme is available on the
[Design](http://localhost:2368/ghost/#/settings/design/) menu option.

```json
{
  "name": "blog-ghost-theme",
  "version": "1.0.0",
  "author": {
    "name": "Allen D. Ball",
    "email": "ball@iprotium.com"
  }
}
```

![](/assets/{{ page.permalink }}/themes.png)

The theme may be activated by clicking on the corresponding `Activate` link.
Ghost provides links to helpful documentation for any errors identified.

![](/assets/{{ page.permalink }}/theme-failed-to-load.png)

## Bootstrap and Prism Integration

Bootstrap dependencies were added to the `package.json` file along with
scripts to deploy to the `assets/` hierarchy.

```json
{
  "name": "blog-ghost-theme",
  "version": "1.0.0",
  "author": {
    "name": "Allen D. Ball",
    "email": "ball@iprotium.com"
  },
  "dependencies": {
    "bootstrap": "^4.1.3",
    "jquery": "^3.3.1",
    "popper.js": "^1.14.4",
    "vendor-copy": "^2.0.0"
  },
  "scripts": {
    "clean": "rm -rf node_modules",
    "postinstall": "vendor-copy"
  },
  "vendorCopy": [
    {
      "from": "node_modules/bootstrap/dist/css/bootstrap.css",
      "to": "assets/css/bootstrap.css"
    },
    {
      "from": "node_modules/bootstrap/dist/js/bootstrap.js",
      "to": "assets/js/bootstrap.js"
    },
    {
      "from": "node_modules/popper.js/dist/popper.js",
      "to": "assets/js/popper.js"
    },
    {
      "from": "node_modules/jquery/dist/jquery.js",
      "to": "assets/js/jquery.js"
    }
  ]
}
```

Prism was configured and downloaded from <https://prismjs.com/download.html>
directly into the `assets` hierarchy.

The `text/css` links are included in `partials/meta-css.hbs`:

{% raw %}
```html
<meta charset="utf-8"/>
<meta http-equiv="X-UA-Compatible" content="IE=edge"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<link rel="stylesheet" type="text/css" href="{{asset "css/bootstrap.css"}}"/>
<link rel="stylesheet" type="text/css" href="{{asset "css/prism.css"}}"/>
<link rel="stylesheet" type="text/css" href="{{asset "css/style.css"}}"/>
<!--[if lt IE 9]>
<script src="https://oss.maxcdn.com/html5shiv/3.7.3/html5shiv.min.js"></script>
<script src="https://oss.maxcdn.com/respond/1.4.2/respond.min.js"></script>
<![endif]-->
```
{% endraw %}

And the `<script/>` directives are included in `partials/javascript.hbs`:

{% raw %}
```html
<script type="text/javascript" src="{{asset "js/jquery.js"}}"></script>
<script type="text/javascript" src="{{asset "js/popper.js"}}"></script>
<script type="text/javascript" src="{{asset "js/bootstrap.js"}}"></script>
<script type="text/javascript" src="{{asset "js/prism.js"}}"></script>
```
{% endraw %}

All integrated into `default.hbs`:

{% raw %}
```html
<!DOCTYPE html>
<html lang="{{lang}}">
  <head>
    {{> meta-css}}
    {{ghost_head}}
  </head>
  <body class="{{body_class}}">
    {{> header}}
    <div class="container">
      {{{body}}}
    </div>
    {{> javascript}}
    {{ghost_foot}}
  </body>
</html>
```
{% endraw %}

where the previous two files are included via the
{% raw %}`{{> meta-css}}`{% endraw %} and
{% raw %}`{{> javascript}}`{% endraw %} directives and the Ghost-rendered
output is placed within a Bootstrap-configured
`<div class="container">...</div>`.  `partials/header.hbs` is also included
through the {% raw %}`{{> header}}`{% endraw %} directive and provides a
Bootstrap 4 "fixed-top" `navbar`.  Some details of that implementation are
described in the next section.

## Bootstrap navbar

The theme provides a `navbar` that is always fixed to the top of the page.
The Bootstrap 4 class names are given in `partials/header.hbs`:

{% raw %}
```html
<nav class="navbar navbar-dark fixed-top bg-dark">
  <div class="container">
    <div class="navbar-header">
      <a class="navbar-brand" href="{{@blog.url}}">{{@blog.title}}</a>
    </div>
  </div>
</nav>
```
{% endraw %}

However, as configured the `navbar` will cover content.  This may be
addressed within `assets/css/style.css`:

```css
body {
  padding-top: 60px;
}

@media (max-width: 979px) {
  body {
    padding-top: 0px;
  }
}
```

## Next Steps

Deploy a production Ghost server (to be covered in a future post).

## References

- [Ghost - Getting Started]
- [Ghost Markdown Guide]
- [Ghost Theme Documentation]
- [Bootstrap]
- [Prism]
- [Reset CSS]
- [Normalize.css]
- [HTML Color Names]

[Ghost]: https://ghost.org/
[Ghost CLI]: https://github.com/TryGhost/Ghost-CLI
[Ghost - Getting Started]: https://docs.ghost.org/v1.0.0/docs/getting-started-guide
[Ghost Markdown Guide]: https://help.ghost.org/article/4-markdown-guide
[Ghost Theme Documentation]: https://themes.ghost.org/docs

[Ansible]: https://www.ansible.com
[Bootstrap]: https://getbootstrap.com/
[Homebrew]: https://brew.sh/
[HTML Color Names]: https://htmlcolorcodes.com/color-names/
[macOS]: https://en.wikipedia.org/wiki/MacOS
[node.js]: https://nodejs.org/
[Normalize.css]: http://necolas.github.io/normalize.css/
[Prism]: https://prismjs.com/
[Reset CSS]: https://meyerweb.com/eric/tools/css/reset/
