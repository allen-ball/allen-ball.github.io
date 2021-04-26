---
title: Notes on Migrating a Blog to GitHub Pages
---

This post discusses my experience migrating my blog from a custom-built
Spring application to GitHub pages.  Unique to the application is the
ability to simultaneously serve markdown and auxiliary HTML files (javadoc)
from the same server.  The application has been hosted on GCP but no longer
works due to some change/upgrade/etc. of a component outside my control.


# Requirements

1. Host blog posts authored in markdown with associated assets

2. Host generated HTML (javadoc) assets associated with the blog posts

3. Serve a custom domain

GitHub pages leveraging Jekyll satisfies requirements #1 and #3.  GitHub
pages may also host the javadoc but it cannot be mixed into the Jekyll
generated site.


# Strategy

* Create a GitHub repository ("blog") to host Jekyll generated site

* Create a GitHub repository ("blog-javadoc") to host the auxiliary javadoc

* Store assets, javadoc, and legacy articles with consistent paths

The legacy application creates URLs of the form
<https://example.com/article/YYYY-MM-DD-title/> for blog posts where
"article" was constant and assets associated with the article are prefixed
with the article URL.  E.g.,

    https://example.com/article/YYYY-MM-DD-title/screenshot.png
    https://example.com/article/YYYY-MM-DD-title/javadoc/index.html

For each legacy article, a "permalink" will be added to the frontmatter of
the form `article/YYYY-MM-DD-title` which will serve the dual purpose of
preserving each article's URL, relative links to other articles, and provide
a mechanism to calculate URLs to article assets and javadoc.


# Initialize the GitHub Repositories

Create a "blog" repository with the <https://github.com/new> workflow with
at least one file (I chose a "Jekyll" `.gitignore`).  Then create a clean
"gh-pages" branch following this
[gist](https://gist.github.com/ramnathv/2227408).

```bash
$ git clone https://github.com/USER/blog.git
Cloning into 'blog'...
remote: Enumerating objects: 3, done.
remote: Counting objects: 100% (3/3), done.
remote: Compressing objects: 100% (2/2), done.
remote: Total 3 (delta 0), reused 0 (delta 0), pack-reused 0
Receiving objects: 100% (3/3), done.
$ cd blog
$ git symbolic-ref HEAD refs/heads/gh-pages
$ rm .git/index
$ cat > .gitignore
_site
.sass-cache
.jekyll-cache
.jekyll-metadata
vendor
^D
$ git add .gitignore
$ git commit -a -m "Created gh-pages branch."
[gh-pages (root-commit) 86d6142] Created gh-pages branch.
 1 file changed, 5 insertions(+)
 create mode 100644 .gitignore
$ git push origin gh-pages
Enumerating objects: 3, done.
Counting objects: 100% (3/3), done.
Delta compression using up to 12 threads
Compressing objects: 100% (2/2), done.
Writing objects: 100% (3/3), 261 bytes | 261.00 KiB/s, done.
Total 3 (delta 0), reused 0 (delta 0), pack-reused 0
remote:
remote: Create a pull request for 'gh-pages' on GitHub by visiting:
remote:      https://github.com/USER/blog/pull/new/gh-pages
remote:
To https://github.com/USER/blog.git
 * [new branch]      gh-pages -> gh-pages
$ git branch --set-upstream-to=origin/gh-pages gh-pages
Branch 'gh-pages' set up to track remote branch 'gh-pages' from 'origin'.
```

To create the blog-javadoc repository either repeat the above procedure or
create from a mirror of the newly created blog repository.  If the latter,
create an *empty* repository with the <https://github.com/new> workflow and
then:

```bash
$ git clone --bare https://github.com/USER/blog.git
Cloning into bare repository 'blog.git'...
remote: Enumerating objects: 6, done.
remote: Counting objects: 100% (6/6), done.
remote: Compressing objects: 100% (4/4), done.
remote: Total 6 (delta 0), reused 3 (delta 0), pack-reused 0
Receiving objects: 100% (6/6), done.
$ cd blog.git
$ git push --mirror https://github.com/USER/blog-javadoc.git
Enumerating objects: 6, done.
Counting objects: 100% (6/6), done.
Delta compression using up to 12 threads
Compressing objects: 100% (4/4), done.
Writing objects: 100% (6/6), 863 bytes | 863.00 KiB/s, done.
Total 6 (delta 0), reused 6 (delta 0), pack-reused 0
To https://github.com/USER/blog-javadoc.git
 * [new branch]      gh-pages -> gh-pages
 * [new branch]      trunk -> trunk
```

After cloning and initializing local copies, be sure to use the `gh-pages`
branch: `git checkout gh-pages`.

```bash
$ gem install bundler jekyll webrick
```


# Install `jekyll`

On MacOS:

```bash
$ brew install ruby
```

And adjust the `${PATH}` to pick-up the newly installed `ruby`:

```bash
$ export PATH="/usr/local/opt/ruby/bin:$PATH
```

```bash
$ gem install bundler jekyll webrick
gem install bundler jekyll webrick
Fetching bundler-2.2.16.gem
Successfully installed bundler-2.2.16
Parsing documentation for bundler-2.2.16
Installing ri documentation for bundler-2.2.16
Done installing documentation for bundler after 4 seconds
Fetching jekyll-4.2.0.gem
...
Successfully installed jekyll-4.2.0
...
Parsing documentation for jekyll-4.2.0
Installing ri documentation for jekyll-4.2.0
Done installing documentation for unicode-display_width, terminal-table, safe_yaml, rouge, forwardable-extended, pathutil, mercenary, liquid, kramdown, kramdown-parser-gfm, ffi, rb-inotify, rb-fsevent, listen, jekyll-watch, sassc, jekyll-sass-converter, concurrent-ruby, i18n, http_parser.rb, eventmachine, em-websocket, colorator, public_suffix, addressable, jekyll after 26 seconds
Fetching webrick-1.7.0.gem
Successfully installed webrick-1.7.0
Parsing documentation for webrick-1.7.0
Installing ri documentation for webrick-1.7.0
Done installing documentation for webrick after 0 seconds
28 gems installed
```

To put `jekyll` on the `${PATH}`:

```bash
export PATH=/usr/local/lib/ruby/gems/3.0.0/bin:$PATH
```


# Populate the blog-javadoc Hierarchy

This process is purely `git`-administrative:

1. Create a docs subdirectory in the gh-pages branch
2. Create a subdirectory and copy the javadoc for each post/article
3. `git add ...` / `git push ...`

To publish, browse <https://github.com/USER/blog-javadoc/settings> and
scroll down to "GitHub Pages" and select "Pages settings now has its own
dedicated tab! [Check it out
here!](https://github.com/USER/blog-javadoc/settings/pages)" and change "/
(root)" to "/docs" and press the "Save" button.  The javadoc assets are now
published at <https://USER.github.io/blog-javadoc/>.  However, since no
`index.html` has been specified in the above process GitHub will return a
`404 File not found` page for the root URL but the article-specific javadoc
artifacts may be found with <https://USER.github.io/blog-javadoc/ARTICLE/>
(as in this
[example](https://allen-ball.github.io/blog-javadoc/2019-03-28-java-streams-and-spliterators/)).

# Create the blog Jekyll Hierarchy

Per the GitHub [directions][Creating a GitHub Pages site with Jekyll]:

```bash
$ cd blog
$ git checkout gh-pages
$ jekylll new docs
```

This will produce:

```
docs
├── 404.html
├── Gemfile
├── Gemfile.lock
├── _config.yml
├── _posts
│   └── 2021-04-25-welcome-to-jekyll.markdown
├── about.markdown
└── index.markdown
```

Edit the `Gemfile` as described in the
[directions][Creating a GitHub Pages site with Jekyll] (adding a line
similar to below) and run `bundle update`.

```gemfile
gem "github-pages", "~> 214", group: :jekyll_plugins
```

I further recommend:

1. Update the parameters in `_config.yml` as necessary
2. Rename `*.markdown` to `*.md`
3. Remove `404.html` and `about.md`
4. Creating the `_drafts` subdirectory and move
   `2021-04-25-welcome-to-jekyll.md` (or your actual first post) there

The site may now be served locally with:

```bash
$ cd blog/docs
$ bundle exec jekyll serve --drafts --livereload
```

If using the "minima" theme, change `index.md` to:

```markdown
---
layout: home
---
```

So posts are included in the home page.  Alternatively, add somehting like
the following to `index.md`:

{% raw %}
```markdown
<ul>
  {% for post in site.posts %}
    <li>
      <a href="{{ post.url }}">{{ post.title }}</a>
      {{ post.excerpt }}
    </li>
  {% endfor %}
</ul>
```
{% endraw %}

I recommend committing this minimal configuration before starting to
customize.  <https://jekyllrb.com/docs/themes/> discusses customization with
the key step to locate a theme directory (e.g., minima):

```bash
$ bundle info --path minima
/usr/local/lib/ruby/gems/3.0.0/gems/minima-2.5.1
```

where individual files may be overridden by creating a "local" copy in the
hierarchy.


# Edits to Legacy Blog Posts

1. Add "permalink" to frontmatter.
   E.g., `permalink: article/2018-08-06-ghost-blog`

2. Change links to non-javadoc assets.
   E.g., change `![](themes.png)` to
   {% raw %}`![](/assets/{{ page.permalink }}/themes.png)`{% endraw %}

3. Change links to javadoc assets.
   E.g., change `[AbstractTaglet](javadoc/ball/tools/javadoc/AbstractTaglet.html)`
   to
   {% raw %}`[AbstractTaglet](https://USER.github.io/blog-javadoc/{{ page.permalink }}/ball/tools/javadoc/AbstractTaglet.html)`{% endraw %}

4. Convert endnotes/footnotes to HTML compatible with GFM


[Creating a GitHub Pages site with Jekyll]: https://docs.github.com/en/pages/setting-up-a-github-pages-site-with-jekyll/creating-a-github-pages-site-with-jekyll
