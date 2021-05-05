---
title: CentOS In-Place Upgrade
canonical_url: https://blog.hcf.dev/article/2020-03-15-centos-in-place-upgrade
tags:
  - CentOS
  - Ansible
permalink: article/2020-03-15-centos-in-place-upgrade
excerpt_separator: <!--more-->
---

[CentOS] 8.0 was released on September 24th, 2019 and [8.1][CentOS 8] on
January 15, 2020.  This article describes how a [CentOS 7] may be upgraded
in place.
<!--more-->
The steps are captured in an [Ansible] role published on
[GitHub](https://github.com/allen-ball/ball-ansible/blob/master/roles/centos/tasks/main.yml).


## Theory of Operation

The steps to migrate a CentOS 7 instance to CentOS 8.1 are:

1. Replace `yum` with `dnf`

    a. Prepare `yum` installation

    b. Install `dnf`

    c. Remove `yum`

2. Use `dnf` to upgrade

    a. Configure CentOS 8.1 packages

    b. Install CentOS 8.1 (userland)

    c. Install CentOS 8.1 kernel

Step #1 is necessitated because
[`dnf`](https://fedoraproject.org/wiki/DNF?rd=Dnf) has
replaced
[`yum`](https://wiki.centos.org/PackageManagement/Yum) in
CentOS/RHEL 8 systems.  The implementation is discussed in the next session.


## Implementation

The following subsection describe the major steps of the upgrade.


### Replace `yum` with `dnf`

The first step is to replace `yum` with `dnf`.  The implementation uses the
fact that `/usr/bin/yum` is a regular file when `yum` is installed and a
symbolic link to `dnf-3` when dnf is installed.

``` yaml
- name: /usr/bin/yum
  stat: path=/usr/bin/yum
  register: yum
```

The condition `yum.stat.isreg is defined and yum.stat.isreg`, if true,
indicates `yum` was the package manager when Anisble was invoked.  The
Ansible script takes advantage of this observation to provide idempotent
operation.  [`package-cleanup(1)`][package-cleanup(1)] is installed from
[`epel-release`][epel-release] and used to remove locally installed RPMs.

{% raw %}
``` yaml
- name: epel-release
  package:
    name: epel-release
    state: latest
  when:
    - yum.stat.isreg is defined and yum.stat.isreg

- name: yum-utils
  package:
    name: yum-utils
    state: latest
  when:
    - yum.stat.isreg is defined and yum.stat.isreg

- name: package-cleanup
  command:
    cmd: "{{ item }}"
  loop:
    - package-cleanup --leaves
    - package-cleanup --orphans
  when:
    - yum.stat.isreg is defined and yum.stat.isreg
```
{% endraw %}

The author's use case is to upgrade a fresh install of CentOS 7.  However,
if the upgrade is to be performed on a configured system, then `rpmconf`
should be invoked to determine if any configuration files need to be
preserved and/or migrated:

``` bash
# yum -y install rpmconf
# rpmconf -a
```

`dnf` is installed with `yum` and then `yum` is removed with the
corresponding `dnf` request, `/etc/yum` is removed, and `dnf` is updated.
It is critical that these steps are completed so the system is not left in
an inconsistent state without a functioning `yum` or `dnf`.

``` yaml
- name: dnf
  package:
    name: dnf
    state: latest
  when:
    - yum.stat.isreg is defined and yum.stat.isreg

- name: yum -> dnf
  shell: |-
    dnf -y remove yum yum-metadata-parser
    rm -rf /etc/yum
    dnf -y upgrade
  args:
    warn: false
  when:
    - yum.stat.isreg is defined and yum.stat.isreg
```

`dnf` is now installed and available to use for an in-place upgrade.


### Upgrade CentOS

[CentOS 8] requires 3 [CentOS] RPMs plus the latest
[`epel-release`][epel-release] (obtained via RPM) which are installed
explicitly with `dnf`.  The conditional `ansible_distribution_major_version
is version(releasever, "lt")` is leveraged to provide idempotent operation
and avoid re-running once CentOS 8 is installed.

{% raw %}
``` yaml
- name: centos_packages
  vars:
    target: 8.1-1.1911.0.8.el8
    arch: "{{ ansible_architecture }}"
    releasever: "{{ target | regex_replace('^([0-9]+)[.].*$', '\\1') }}"
    BaseOS: "http://mirror.centos.org/centos/{{ releasever }}/BaseOS"
    Packages: "{{ BaseOS }}/{{ arch }}/os/Packages"
  set_fact:
    releasever: "{{ releasever }}"
    centos_packages:
      - "{{ Packages }}/centos-gpg-keys-{{ target }}.noarch.rpm"
      - "{{ Packages }}/centos-release-{{ target }}.{{ arch }}.rpm"
      - "{{ Packages }}/centos-repos-{{ target }}.{{ arch }}.rpm"
      - "https://dl.fedoraproject.org/pub/epel/epel-release-latest-{{ releasever }}.noarch.rpm"
```
{% endraw %}

The system is now ready for the actual upgrade.  The CentOS Upgrade script
has to run until reboot or the system will be left in an inconsistent state.
Unfortunately `python` is replaced (and moved) so the Anisble client loses
communication during the process eliminating the possibility of using the
Anisble `reboot` module.  Instead, the administrator should reinvoke the
Ansible play once the update and reboot are complete.

{% raw %}
``` yaml
- name: warn
  debug:
    msg: >-
      Warning: CentOS Upgrade will install kernel and initiate reboot
  when:
    - ansible_distribution_major_version is version(releasever, "lt")

- name: CentOS Upgrade
  shell: |-
    dnf -y install {{ centos_packages | join(" ") }}
    dnf clean all
    rpm -e $(rpm -q kernel)
    rpm -e --nodeps sysvinit-tools
    dnf -y --releasever={{ releasever }} --allowerasing --setopt=deltarpm=false distro-sync
    dnf -y install kernel-core
    dnf -y groupupdate "Core" "Minimal Install"
    shutdown -r now
  args:
    warn: false
  when:
    - ansible_distribution_major_version is version(releasever, "lt")
```
{% endraw %}


### Post Upgrade Steps

The script allows for enabling the `CentOS-Plus` repository and updating
installed packages *after* the reboot.

``` yaml
- name: Enable CentOS-Plus repository
  ini_file:
    dest: /etc/yum.repos.d/CentOS-centosplus.repo
    create: no
    section: centosplus
    option: enabled
    value: "1"

- name: package update
  package:
    name: "*"
    state: latest
```


## Summary

[CentOS 7] installation may be upgraded to [CentOS 8] in-place once `yum` is
replaced by `dnf`.


[CentOS]: https://centos.org/
[CentOS 7]: https://wiki.centos.org/Manuals/ReleaseNotes/CentOS7.1908
[Centos 8]: https://wiki.centos.org/Manuals/ReleaseNotes/CentOS8.1911

[epel-release]: https://fedoraproject.org/wiki/EPEL

[Ansible]: https://www.ansible.com/

[package-cleanup(1)]: https://linux.die.net/man/1/package-cleanup
