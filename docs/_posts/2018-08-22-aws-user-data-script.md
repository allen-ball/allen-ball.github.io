---
title: AWS EC2 User Data Shell Script
canonical_url: https://blog.hcf.dev/article/2018-08-22-aws-user-data-script/
tags:
  - AWS
  - EBS
  - EC2
  - automount
  - Ansible
permalink: article/2018-08-22-aws-user-data-script
excerpt_separator: <!--more-->
---

Creators of [Amazon Elastic Compute Cloud (EC2)][EC2] instances may stuff a
script into the "user data" which will be executed on the instance's initial
boot.  This script may be useful to mount [Elastic Block Store (EBS)][EBS]
volumes as file systems since those volumes cannot be attached to the
instance until *after* the instance has been created and started.

This article presents a script which leverages the functions provided in the
`aws.rc` script described in a previous
[article](/article/2018-08-20-auto-ebs-map).
<!--more-->
The "user data" script described herein provides the following
services to Redhat and CentOS instances:

1. Update OS software:

    a. `yum update`

    b. Install/Update Python

    c. Install AWS CLI

2. Create users, install their SSH authorized keys, and configure `sudo`

3. Attach volumes, create file systems (if neccessary), mount, and update
   [/etc/fstab][fstab(5)]


## AWS Configuration

The scripts require specific configuration in AWS.  These requirements are
described in the next subsections.


### Users

The script will configure all users specified in the instance's
[http://169.254.169.254/latest/meta-data/public-keys/](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html).
These key pairs must be imported either through [AWS Management Console] or
the [AWS CLI][AWS CLI import-key-pair].  The name of the key pair must be
the same as the user name.

A primary use case is to create and configure `ec2-user` on CentOS images to
be consistent with Amazon's Linux images.


### EBS File System Volumes

Any [EBS] volume that will be mounted as a file system must be configured
with the following
[tags](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_Tags.html):

* `host`
* `fstype`
* `mntpt`

Where `host` is the
[http://169.254.169.254/latest/meta-data/local-ipv4](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html)
address of the newly created instance, `fstype` is a file system type
compatible with the [mkfs(8)] and [mount(8)] commands'`-t` argument, and
`mntpt` is the local directory on which the file system will be mounted.

Once the [EBS] volume is formatted with a valid file system, the volume's
`uuid` tag should be updated with the file system's UUID.  The
`user-data.bash` script will *not* format the volume if the `uuid` tag is
present.  The `user-data.bash` script will update the volume's `uuid` tag if
it successfully creates a file system on the volume.

Finally, the script will configure [/etc/fstab][fstab(5)] to mount the [EBS]
volume on the `mntpt` directory.


## Theory of Operation

The script:

1. Update the operating system software
2. Configures the users whose keys are specified in
   [http://169.254.169.254/latest/meta-data/public-keys/](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html)
3. Attaches, formats, and mounts any EBS volumes tagged with this instance's
   [http://169.254.169.254/latest/meta-data/local-ipv4](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html)
   address

The corresponding parts of the `user-data.bash` script are described in
detail in the following subsections.


### Software Update

The software update consists of:

1. Updating all packages managed by [yum(8)]
2. Install and update [python(1)]
3. Install the [AWS Command Line Interface]

```bash
export LANG=en_US.UTF-8
export LC_ALL=${LANG}

yum -y update
yum -y install python
easy_install --prefix /usr pip
pip install --prefix /usr --upgrade pip
pip install --prefix /usr --upgrade awscli
```


### Users Configuration

For each public key specified in
[http://169.254.169.254/latest/meta-data/public-keys/](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html):

1. If they do not exist, create the user specified by the key name and
   create that user's home directory
2. Add the
   [openssh-key](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html)
   value to the user's `~/.ssh/authorized_keys`
3. Configure the user in [sudoers(5)]

```bash
for key in $(metadata public-keys/); do
    username=${key#*=}

    useradd -G wheel -m -s /bin/bash -U ${username}
    userhome=$(eval echo ~${username})

    mkdir -p ${userhome}/.ssh
    echo "$(metadata public-keys/${key%%=*}/openssh-key)" \
         >> ${userhome}/.ssh/authorized_keys
    chown -R ${username}:${username} ${userhome}/.ssh
    chmod -R go-rwx ${userhome}/.ssh

    file=/etc/sudoers.d/user-data-${username}
    echo "${username} ALL=(ALL) NOPASSWD:ALL" > /Users/ball/hcf-dev/blog/2018-08-22-aws-user-data-script/pom.xml
    chmod a-wx,o-r /Users/ball/hcf-dev/blog/2018-08-22-aws-user-data-script/pom.xml
done
```


### Attach and Mount EBS Volumes

For each [EBS] volume tagged with `host` equalling the value at
[http://169.254.169.254/latest/meta-data/local-ipv4](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html),
a non-nil `fstype`, and non-nil `mntpt`:

1. [Attach](https://docs.aws.amazon.com/cli/latest/reference/ec2/attach-volume.html)
   the [EBS] volume to this instance
2. Test if the volume contains data known to [file(1)] and, if it does
   *not*, [create a file system][mkfs(8)] of type specified by `fstype`
4. Determine the UUID of the file system and add an entry to
   [/etc/fstab][fstab(5)]

```bash
export HOST=$(metadata local-ipv4)
export VOLUMES=$(ec2 describe-volumes \
                       --filters Name=tag:host,Values=${HOST} \
                       --output text --query 'Volumes[*].VolumeId')

if [ -n "${VOLUMES}" ]; then
    for volume in ${VOLUMES}; do
        fstype=$(ec2-get-tag-value ${volume} fstype)

        if [ "${fstype}" != "" ]; then
            device=$(next-unattached-block-device)

            ec2-attach-volume ${volume} ${device}

            if [ "$(file -b -s ${device})" == "data" ]; then
                volume-mkfs ${volume} ${device} ${fstype}
            fi

            uuid=$(ec2-get-tag-value ${volume} uuid)
            mntpt=$(ec2-get-tag-value ${volume} mntpt)

            if [ "${uuid}" != "" -a "${mntpt}" != "" ]; then
                mkdir -p ${mntpt}
                echo "UUID=${uuid} ${mntpt} ${fstype} defaults 0 2" \
                     >> /etc/fstab
            fi
        fi
    done
fi
```

At this point in the script, the file system could be mounted with
[`mount -a`][mount(8)].  However, the script reboots the instance and the
file systems are booted on start-up.


## user-data.bash

For reference, the complete `user-data.bash` [Ansible] template is included
below.  The `aws.rc` script is included through a relative path.  If another
tool than Ansible is used, the `aws.rc` script must be included to provide
the functions through that tool's appropriate mechanism.

{% raw %}
```bash
#!/bin/bash
# ----------------------------------------------------------------------------
# user-data.bash
# ----------------------------------------------------------------------------
export LANG=en_US.UTF-8
export LC_ALL=${LANG}

yum -y update
yum -y install python
easy_install --prefix /usr pip
pip install --prefix /usr --upgrade pip
pip install --prefix /usr --upgrade awscli
# ----------------------------------------------------------------------------
# Functions
# ----------------------------------------------------------------------------
{{ lookup('template', '../../aws.rc/templates/etc/aws.rc') }}
# ----------------------------------------------------------------------------
# Create users and install respective .ssh/authorized_keys for public-keys'
# metadata
# ----------------------------------------------------------------------------
for key in $(metadata public-keys/); do
    username=${key#*=}

    useradd -G wheel -m -s /bin/bash -U ${username}
    userhome=$(eval echo ~${username})

    mkdir -p ${userhome}/.ssh
    echo "$(metadata public-keys/${key%%=*}/openssh-key)" \
         >> ${userhome}/.ssh/authorized_keys
    chown -R ${username}:${username} ${userhome}/.ssh
    chmod -R go-rwx ${userhome}/.ssh

    file=/etc/sudoers.d/user-data-${username}
    echo "${username} ALL=(ALL) NOPASSWD:ALL" > /Users/ball/hcf-dev/blog/2018-08-22-aws-user-data-script/pom.xml
    chmod a-wx,o-r /Users/ball/hcf-dev/blog/2018-08-22-aws-user-data-script/pom.xml
done
# ----------------------------------------------------------------------------
# Attach local volumes and manage file systems
#
# File system volumes must define the following tags:
#         host
#         fstype
#         mntpt
#
# This script will update the volume's "uuid" tag.
# ----------------------------------------------------------------------------
export HOST=$(metadata local-ipv4)
export VOLUMES=$(ec2 describe-volumes \
                       --filters Name=tag:host,Values=${HOST} \
                       --output text --query 'Volumes[*].VolumeId')

if [ -n "${VOLUMES}" ]; then
    for volume in ${VOLUMES}; do
        fstype=$(ec2-get-tag-value ${volume} fstype)

        if [ "${fstype}" != "" ]; then
            device=$(next-unattached-block-device)

            ec2-attach-volume ${volume} ${device}

            if [ "$(file -b -s ${device})" == "data" ]; then
                volume-mkfs ${volume} ${device} ${fstype}
            fi

            uuid=$(ec2-get-tag-value ${volume} uuid)
            mntpt=$(ec2-get-tag-value ${volume} mntpt)

            if [ "${uuid}" != "" -a "${mntpt}" != "" ]; then
                mkdir -p ${mntpt}
                echo "UUID=${uuid} ${mntpt} ${fstype} defaults 0 2" \
                     >> /etc/fstab
            fi
        fi
    done
fi

#mount -a
shutdown -r now

exit 0
```
{% endraw %}

Within [Ansible], the `user-data.bash` can be expanded from the template
into a "fact:"

{% raw %}
```yaml
- name: user-data script
  set_fact:
    user_data: >
      {{ lookup('template', 'user-data.bash') }}
```
{% endraw %}

An example fragment for creating the [EC2] instance with the `user-data.bash`
script is given below.

{% raw %}
```yaml
- name: 172.31.0.4
  ec2:
    ...
    instance_profile_name: ec2-user
    key_name: ec2-user
    user_data: >
      {{ user_data }}
    ...
```
{% endraw %}


## References

- [Source]
- [automount/autofs Executable Map for Amazon EBS Volumes]
- [Amazon Web Services (AWS)][AWS]
- [AWS Elastic Compute Cloud (EC2)][EC2]
- [AWS Elastic Block Store (EBS)][EBS]
- [Ansible]


[Ansible]: https://www.ansible.com/

[AWS]: https://aws.amazon.com/
[AWS Command Line Interface]: https://aws.amazon.com/cli/
[AWS CLI import-key-pair]: https://docs.aws.amazon.com/cli/latest/reference/ec2/import-key-pair.html
[EBS]: https://aws.amazon.com/ebs/
[EC2]: https://aws.amazon.com/ec2/
[AWS Management Console]: https://console.aws.amazon.com/organizations/home

[Source]: https://github.com/allen-ball/ball-ansible/tree/master/roles/aws-user-data

[automount/autofs Executable Map for Amazon EBS Volumes]: /article/2018-08-20-auto-ebs-map

[file(1)]: https://linux.die.net/man/1/file
[python(1)]: https://linux.die.net/man/1/python
[fstab(5)]: https://linux.die.net/man/5/fstab
[sudoers(5)]: https://linux.die.net/man/5/sudoers
[mkfs(8)]: https://linux.die.net/man/8/mkfs
[mount(8)]: https://linux.die.net/man/8/mount
[yum(8)]: https://linux.die.net/man/8/yum
