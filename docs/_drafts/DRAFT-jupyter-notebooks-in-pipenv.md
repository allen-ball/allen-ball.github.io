---
email: ball@hcf.dev
title: Jupyter Notebooks in pipenv Environment
permalink: article/DRAFT-jupyter-notebooks-in-pipenv
tags:
 - Jupyter
---

Notes from setting up `jupyter notebook` in a `pipenv` environment.

## Prerequisites

```
brew "python"
brew "pipenv"
brew "pyenv"
```

```
brew "node"
brew "npm"
```

```
brew "scala"
brew "sbt"
```

## Baseline Configuration

```makefile
# ----------------------------------------------------------------------------
# Makefile
# ----------------------------------------------------------------------------
export LANG=en_US.UTF-8
export LC_ALL=$(LANG)

export PIPENV_VENV_IN_PROJECT=1

ifeq ("$(shell echo $$TERM)","dumb")
export PIPENV_COLORBLIND=1
export PIPENV_HIDE_EMOJIS=1
export PIPENV_NOSPIN=1
endif

PIPENV=pipenv --bare
DOTENV=$(PIPENV) run dotenv -f $(DOT_ENV)
JUPYTER=$(PIPENV) run jupyter

PIPFILE=Pipfile
DOT_VENV=.venv
DOT_ENV=.env
ENVVARS=
# ----------------------------------------------------------------------------
# JupyterLab
# ----------------------------------------------------------------------------
KERNELS=
NBEXTENSIONS=

all:	$(DOT_VENV)

PACKAGES+=jupyterlab ipython virtualenv
PACKAGES+=numpy pandas pandoc
PACKAGES+=matplotlib seaborn
PACKAGES+=nbconvert nbopen
# ----------------------------------------------------------------------------
ENVVARS+=JAVA_HOME
ifeq ("$(shell uname -s)","Darwin")
export JAVA_HOME?=$(shell /usr/libexec/java_home -v 1.8)
endif
ifeq ("$(shell uname -s)","Linux")
export JAVA_HOME?=/usr/lib/jvm/adoptopenjdk-8-hotspot-amd64
endif
# ----------------------------------------------------------------------------
ENVVARS+=SPARK_HOME
SPARK_VERSION=3.0.1
ifeq ("$(shell uname -s)","Darwin")
SPARK_HOME?=/usr/local/opt/apache-spark/libexec
endif
PACKAGES+=pyspark==$(SPARK_VERSION) py4j
# ----------------------------------------------------------------------------
# https://almond.sh/ https://github.com/almond-sh/almond
KERNELS+=almond
ALMOND_VERSION?=0.10.8
SCALA_VERSION?=2.12.10
SPARK_VERSION?=3.0.1

kernel-almond:
	curl -sLo coursier https://git.io/coursier-cli
	@chmod +x coursier
	$(PIPENV) run ./coursier launch \
		--fork almond:$(ALMOND_VERSION) --scala $(SCALA_VERSION) -- \
		--install --force --jupyter-path .venv/share/jupyter/kernels
	@-rm -rf coursier

clean::
	@-rm -rf coursier
# ----------------------------------------------------------------------------
# https://toree.apache.org/ https://github.com/apache/incubator-toree
#KERNELS+=toree
#PACKAGES+=toree
PACKAGES+=https://dist.apache.org/repos/dist/dev/incubator/toree/0.5.0-incubating-rc1/toree-pip/toree-0.5.0.tar.gz

kernel-toree:
	$(JUPYTER) toree install --interpreters=Scala,SQL \
		--python=$(shell $(PIPENV) run which python) \
		--sys-prefix
# ----------------------------------------------------------------------------
NBEXTENSIONS+=widgetsnbextension
PACKAGES+=ipywidgets
# ----------------------------------------------------------------------------
# Polynote
POLYNOTE_URL?=https://github.com/polynote/polynote/releases/download/0.3.12/polynote-dist-2.12.tar.gz

polynote: $(DOT_VENV)
	curl -sL $(POLYNOTE_URL) | tar xCf $(DOT_VENV)/bin -

PACKAGES+=jedi>=0.16.0 jep==3.9.0
# pipenv run .venv/bin/polynote/polynote.py -c ${PWD}/config.yml
# ----------------------------------------------------------------------------
$(PIPFILE) $(DOT_VENV):
	@$(MAKE) $(DOT_ENV)
	$(PIPENV) install $(PACKAGES) $(NBEXTENSIONS)
	$(PIPENV) run ipython kernel install --sys-prefix
	@$(MAKE) kernels
	@$(MAKE) nbextensions

clean::
	@-$(PIPENV) --rm
	@-rm -rf $(PIPFILE) $(PIPFILE).lock

$(DOT_ENV):
	$(PIPENV) install python-dotenv[cli]
	@touch $(DOT_ENV)
	@$(MAKE) envvars

envvars: $(addprefix setenv-, $(ENVVARS))

setenv-%:
	@-$(DOTENV) $(if $(value $*),set,unset) $* $($*)

clean::
	@-rm -rf $(DOT_ENV)

kernels: $(addprefix kernel-, $(KERNELS))
	$(JUPYTER) kernelspec list

kernel-%:
	@$(MAKE) $@

nbextensions: $(addprefix nbextension-enable-, $(NBEXTENSIONS))
	$(JUPYTER) nbextension list

nbextension-enable-%:
	$(JUPYTER) nbextension enable --py $* --sys-prefix
```

![](/assets/{{ page.permalink }}/screen-shot-1.png)

## Emacs

```lisp
(use-package ein
  :ensure t
  :commands (ein:notebooklist-open)
  :config (progn
            (setq ein:jupyter-default-notebook-directory "~/Notebooks")
            (setq ein:jupyter-server-command "~/Notebooks/.venv/bin/jupyter")))
```

## Additional Kernels

### Almond

## Notebook Extensions
