---
title: automount/autofs Executable Map for Amazon EBS Volumes
canonical_url: https://blog.hcf.dev/article/2018-08-20-auto-ebs-map/
tags:
  - AWS
  - EBS
  - EC2
  - autofs
  - automount
  - systemd
  - Ansible
permalink: article/2018-08-20-auto-ebs-map
---

Amazon Web Services (AWS) provides their Elastic Block Store (EBS) service
for persistent block storage for Amazon Elastic Compute Cloud (EC2)
instances in the AWS cloud.  Linux/UNIX file systems may be created on these
volumes and then attached to an instance and subsequently mounted as a file
system.  However, the EC2 instance must be started before the volume can be
attached which may lead to significant challenges in architecture design as
both the numbers of instances and volumes increase.

This article presents an implementation of an executable automount map which
may be leveraged to attach and mount EBS volumes on demand.  The
implementation includes a mechanism for detaching unmounted EBS volumes so
they me be attached to different instances in the future.

## Theory of Operation

EBS volumes must be prepared with file systems and must be
[tagged](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_Tags.html)
with `fstype` and `uuid`.  The `fstype` value must accurately reflect the
the volume's file system type and must be supported by the EC2 instance.
The `uuid` tag must contain the file system partition UUID.[^1]

[^1]: The [`aws.rc` script](#aws.rc) included at the end of this article
includes shell functions for allocating, formatting, and tagging EBS volumes
as file systems.

The volumes are attached and mounted on demand through an *Executable Autofs
Map*.  [systemd](https://en.wikipedia.org/wiki/Systemd) is
configured to monitor the corresponding `autofs` mount point to detect when
the `automount` daemon unmounts a file system and then detach the volume
from the EC2 instance.

## Implementation

EBS volumes are attached with the [auto.ebs Executable Map](#auto.ebs) and
are detached with [systemd auto.ebs-detach.service](#systemd) (each
described in the following subsections.

The scripts herein rely on shell functions defined in `/etc/aws.rc`
described [later](#aws.rc) in this article.  Most functions are
straightforward wrappers to the corresponding [AWS Command Line
Interface](https://aws.amazon.com/cli/).

### <a name="auto.ebs"></a> `auto.ebs` Executable Map

The `auto.ebs` Executable Map consists of two configuration files:

```bash
/
└── etc
    ├── auto.ebs
    └── auto.master.d
        └── ebs.autofs
```

The work is done by the `/etc/auto.ebs` script.  The volume must be properly
formatted as a file system and it must have accurate `fstype` and `uuid`
tags.  If the volume is "available," it is simply attached to a block device
and its map entry is constructed based on the `fstype` and `uuid` tags.

If the volume is "in-use," but if the volume is attached to *this* instance,
then a map entry is returned as follows:

1. If the volume is the special case of matching the AMI block device, a map
   entry with `fstype=bind,symlink` with a target of `/` is returned, else,
2. If the volume is already mounted to *this* instance (outside the `autofs`
   map), a map entry with `fstype=bind,symlink` with a target of the
   volume's mount point is returned, else,
3. The behavior is the same as if the volume was "available" (described
   above)

```bash
#!/bin/bash
# ----------------------------------------------------------------------------
# /etc/auto.ebs
# ----------------------------------------------------------------------------
. /etc/aws.rc

# auto-ebs-entry key(volume)
auto-ebs-entry() {
    local key="$1"
    local value=""

    local fstype=$(ec2-get-tag-value ${key} fstype)
    local uuid=$(ec2-get-tag-value ${key} uuid)

    case $(ec2-get-volume-state ${key}) in
        available)
            if [ "${fstype}" != "" -a "${uuid}" != "" ]; then
                ec2-attach-volume ${key} $(next-unattached-block-device) 1>&2

                value="-fstype=${fstype} :UUID=${uuid}"
            fi
            ;;

        in-use)
            local instance="$(ec2-get-volume-attachment-instance ${key})"
            local state="$(ec2-get-volume-attachment-state ${key})"

            if [ "${instance}" == "${INSTANCE}" -a "${state}" == "attached" ]; then
                local device="$(ec2-get-volume-attachment-device ${key})"

                if [ "$(metadata block-device-mapping/ami)" == "${device}" ]; then
                    value="-fstype=bind,symlink :/"
                else
                    local mntpt=$(lsblk -no MOUNTPOINT ${device})

                    if [ "${mntpt}" != "" ]; then
                        value="-fstype=bind,symlink :${mntpt}"
                    elif [ "${fstype}" != "" -a "${uuid}" != "" ]; then
                        value="-fstype=${fstype} :UUID=${uuid}"
                    fi
                fi
            else
                echo "${key} already in-use" 1>&2
            fi
            ;;

        *)
            echo "Cannot mount ${key}" 1>&2
            ;;
    esac

    echo "$0: key=\"${key}\" value=\"${value}\"" 1>&2

    if [ "${value}" != "" ]; then
        echo "${value}"
    fi
}

auto-ebs-entry $1
```

The `/ebs` mount point and associated executable map script must be
configured in `/etc/auto.master.d/ebs.autofs`.

```bash
# /etc/auto.master.d/ebs.autofs
/ebs    /etc/auto.ebs
```

### <a name="systemd"></a> `systemd` `auto.ebs-detach.service`

`systemd` is configured to monitor the `/ebs` map mount directory and invoke
`/etc/auto.ebs-detach.sh` whenever the directory changes.  This script is
responsible for detaching any volumes that are no longer mounted.
`/usr/lib/systemd/system/auto.ebs-detach.service` configures
`/etc/auto.ebs-detach.sh` as a service and
`/usr/lib/systemd/system/auto.ebs-detach.path` configures `systemd` to
monitor `/ebs`.

```bash
/
├── etc
│   └── auto.ebs-detach.sh
└── usr
    └── lib
        └── systemd
            └── system
                ├── auto.ebs-detach.path
                └── auto.ebs-detach.service
```

Since the only reliable event[^2] that is available to monitor is a change
to `/ebs` directory, each attached volume must be checked in each
invocation.

[^2]: The author investigated implementations based on
[incrond(8)](https://linux.die.net/man/8/incrond) and
[inotifywait(1)](https://linux.die.net/man/1/inotifywait) but
settled on `systemd` as it appears to be the greatest common denominator for
Linux distributions.

```bash
#!/bin/bash
# ----------------------------------------------------------------------------
# /etc/auto.ebs-detach.sh
# ----------------------------------------------------------------------------
. /etc/aws.rc

# detach-volume-if-not-mounted key(volume)
detach-volume-if-not-mounted() {
    local key="$1"
    local instance="$(ec2-get-volume-attachment-instance ${key})"
    local state="$(ec2-get-volume-attachment-state ${key})"

    if [ "${instance}" == "${INSTANCE}" -a "${state}" == "attached" ]; then
        local device="$(ec2-get-volume-attachment-device ${key})"

        if [ "$(metadata block-device-mapping/ami)" != "${device}" ]; then
            local mntpt=$(lsblk -no MOUNTPOINT ${device})

            if [ "${mntpt}" == "" ]; then
                ec2-detach-volume ${key}
            fi
        fi
    fi
}

for volume in $(list-attached-volumes); do
    detach-volume-if-not-mounted ${volume}
done

exit 0
```

`/usr/lib/systemd/system/auto.ebs-detach.service` defines the script as a
service.

```ini
# /usr/lib/systemd/system/auto.ebs-detach.service
[Unit]
Description=/etc/auto.ebs-detach.sh
After=autofs.service

[Service]
Type=oneshot
ExecStart=/etc/auto.ebs-detach.sh

[Install]
WantedBy=multi-user.target
```

`/usr/lib/systemd/system/auto.ebs-detach.path` configures `systemd` to monitor `/ebs`.

```ini
# /usr/lib/systemd/system/auto.ebs-detach.path
[Unit]
Description=PathModified=/ebs/ /etc/auto.ebs-detach.sh

[Path]
PathModified=/ebs/

[Install]
WantedBy=multi-user.target
```

### Ansible Role

The [Ansible](https://www.ansible.com/) tasks and handlers
configure the `autofs` `/ebs` map.

```yaml
# tasks/main.yml
---
- name: auto.ebs config files
  template:
    src: "{{ item.path }}"
    dest: "/{{ item.path }}"
    mode: "{{ item.mode }}"
  with_items:
    - { path: etc/auto.master.d/ebs.autofs, mode: "0644" }
    - { path: etc/auto.ebs, mode: "0755" }
    - { path: etc/auto.ebs-detach.sh, mode: "0755" }
    - { path: usr/lib/systemd/system/auto.ebs-detach.service, mode: "0644" }
    - { path: usr/lib/systemd/system/auto.ebs-detach.path, mode: "0644" }
  notify: [ 'reload systemd', 'restart autofs' ]

- name: enable auto.ebs-detach.service
  service:
    name: "{{ item }}"
    enabled: yes
    state: started
  with_items:
    - auto.ebs-detach.service
    - auto.ebs-detach.path
  notify: [ 'reload systemd' ]
```

```yaml
# handlers/main.yml
---
- name: reload systemd
  systemd: daemon_reload=yes

- name: restart autofs
  service: name=autofs enabled=yes state=restarted
```

### SELinux Policies

If Security-Enhanced Linux (SELinux) is installed and enabled, the security
policies need to be adjusted to allow the scripts access to some resources.

```clike
module local-automount 1.0;

require {
	type ldconfig_exec_t;
	type automount_t;
	type fixed_disk_device_t;
	class blk_file getattr;
	class file { execute execute_no_trans open read };
}

#============= automount_t ==============
allow automount_t fixed_disk_device_t:blk_file getattr;
allow automount_t ldconfig_exec_t:file { execute execute_no_trans open read };
```

These policies may be installed with the combination of the
`checkmodule`/`semodule_package`/`semodule` commands.  The following is
added to the Ansible `tasks/main.yml`.

```yaml
- name: SELinux Policies
  set_fact:
    policies:
      - local-automount

- name: SELinux (*.te)
  template:
    src: "selinux/{{ item }}.te"
    dest: "/etc/selinux/tmp/{{ item }}.te"
  with_items:
    - "{{ policies }}"
  register: te
  when: >
    ansible_selinux is defined
    and ansible_selinux != False
    and ansible_selinux.status == 'enabled'

- name: SELinux - checkmodule
  command: >
    chdir=/etc/selinux/tmp creates={{ item }}.mod
    checkmodule -M -m -o {{ item }}.mod {{ item }}.te
  with_items:
    - "{{ policies }}"
  register: mod
  when: te.changed

- name: SELinux - semodule_package
  command: >
    chdir=/etc/selinux/tmp creates={{ item }}.pp
    semodule_package -o {{ item }}.pp -m {{ item }}.mod
  with_items:
    - "{{ policies }}"
  register: pp
  when: mod.changed

- name: SELinux - semodule
  command: >
    chdir=/etc/selinux/tmp
    semodule -i {{ item }}.pp
  with_items:
    - "{{ policies }}"
  when: pp.changed
```

Developing the SELinux polices will be the subject of a future entry.

### <a name="aws.rc"></a> /etc/aws.rc

`/etc/aws.rc` provides the common shell functions used by the scripts
described above.  In addition, the `volume-mkfs` and `new-volume-mkfs` shell
functions demonstrate the necessary tags for file system volumes to be
managed by the `/ebs` map.

```bash
#!/bin/bash
# ----------------------------------------------------------------------------
# /etc/aws.rc
# ----------------------------------------------------------------------------
# Functions
# ----------------------------------------------------------------------------
# metadata data
metadata() {
    curl -s http://169.254.169.254/latest/meta-data/$1
}

export ZONE=$(metadata placement/availability-zone)
export REGION=$(echo ${ZONE} | sed 's/\(.*\)[a-z]/\1/')
export INSTANCE=$(metadata instance-id)

# ec2 command [argument ...]
ec2() {
    aws ec2 --region ${REGION} "$@"
}

# ec2-get-tag-value resource-id key
ec2-get-tag-value() {
    ec2 describe-tags \
        --filters Name=resource-id,Values="$1" Name=key,Values="$2" \
        --output text --query 'Tags[*].Value'
}

# ec2-set-tag-value resource-id key value
ec2-set-tag-value() {
    ec2 delete-tags --resources "$1" --tags Key="$2"
    ec2 create-tags --resources "$1" --tags Key="$2",Value="$3"
}

# ec2-get-volume-state volume-id
ec2-get-volume-state() {
    ec2 describe-volumes \
        --volume-ids "$1" \
        --output text --query 'Volumes[].State'
}

# ec2-get-volume-attachment-instance volume-id
ec2-get-volume-attachment-instance() {
    local value=$(ec2 describe-volumes \
                      --volume-ids "$1" \
                      --query 'Volumes[].Attachments[].InstanceId' \
                      --output text)

    echo ${value}
}

# ec2-get-volume-attachment-state volume-id
ec2-get-volume-attachment-state() {
    local value=$(ec2 describe-volumes \
                      --volume-ids "$1" \
                      --query 'Volumes[].Attachments[].State' \
                      --output text)

    echo ${value}
}

# ec2-get-volume-attachment-device volume-id
ec2-get-volume-attachment-device() {
    local value=$(ec2 describe-volumes \
                      --volume-ids "$1" \
                      --query 'Volumes[].Attachments[].Device' \
                      --output text)

    echo ${value}
}

# ec2-attach-volume volume-id device
ec2-attach-volume() {
    echo $(ec2 attach-volume \
               --instance-id ${INSTANCE} --volume-id "$1" --device "$2" \
               --output text) 1>&2
    ec2 wait volume-in-use --volume-ids "$1"

    while [ "$(ec2-get-volume-attachment-state $1)" != "attached" ]; do
        sleep 15
    done
}

# ec2-detach-volume volume-id
ec2-detach-volume() {
    echo $(ec2 detach-volume \
               --instance-id ${INSTANCE} --volume-id "$1" \
               --output text) 1>&2
    ec2 wait volume-available --volume-ids "$1"
}

# list-attached-volumes
list-attached-volumes() {
    local value=$(ec2 describe-instances \
                      --instance-ids ${INSTANCE} \
                      --output text \
                      --query 'Reservations[].Instances[].BlockDeviceMappings[].Ebs.VolumeId')

    echo ${value}
}

# next-unattached-block-device
next-unattached-block-device() {
    local attached=($(lsblk -ndo NAME))
    local available=($(echo -e ${attached[0]:0:-1}{a..z}\\n))

    for name in "${attached[@]}"; do
        available=(${available[@]//*${name}*})
    done

    echo /dev/${available[0]}
}

# volume-mkfs volume-id device fstype
volume-mkfs() {
    mkfs -t "$3" "$2"

    local uuid=""
    while [ -z "${uuid}" ]; do
        uuid=$(lsblk -no UUID ${device})
        sleep 10
    done

    ec2-set-tag-value "$1" fstype "$3"
    ec2-set-tag-value "$1" uuid ${uuid}
}

# new-volume-mkfs volume-type size fstype
new-volume-mkfs() {
    local volume=$(ec2 create-volume \
                       --availability-zone ${ZONE} \
                       --volume-type "$1" --size "$2" \
                       --output text --query 'VolumeId')

    ec2 wait volume-available --volume-ids ${volume}

    local device=$(next-unattached-block-device)

    ec2-attach-volume ${volume} ${device} 1>&2

    volume-mkfs ${volume} ${device} "$3" 1>&2

    ec2-detach-volume ${volume} 1>&2

    echo ${volume}
}
```

## References

- [Source](https://github.com/allen-ball/ball-ansible/tree/master/roles/auto.ebs)
- [Amazon Web Services (AWS)](https://aws.amazon.com/)
- [AWS Elastic Block Store (EBS)](https://aws.amazon.com/ebs/)
- [AWS Elastic Compute Cloud (EC2)](https://aws.amazon.com/ec2/)
- [Ansible](https://www.ansible.com/)
