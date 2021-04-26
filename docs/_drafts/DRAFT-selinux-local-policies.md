---
title: SELinux Local Policies
tags:
 - Linux
 - SELinux
---

To review audit log:

$ sudo audit2allow -a


Cloud Mailbox local policies:

    $ sudo cat /var/log/audit/audit.log \
      | grep automount \
      | audit2allow -a -M local-automount
    $ sudo semodule -i local-automount.pp

    $ sudo cat /var/log/audit/audit.log \
      | grep dovecot \
      | audit2allow -a -M local-dovecot
    $ sudo semodule -i local-dovecot.pp

    $ sudo cat /var/log/audit/audit.log \
      | grep postfix \
      | audit2allow -a -M local-postfix
    $ sudo semodule -i local-postfix.pp


To install from *.te file:

    $ checkmodule -M -m -o local-automount.mod local-automount.te
    $ semodule_package -o local-automount.pp -m local-automount.mod
    $ sudo semodule -i local-automount.pp

