---
title: AWS VPC Set-Up with Ansible
canonical_url: https://blog.hcf.dev/article/2020-03-16-aws-vpc-with-ansible/
tags:
  - AWS
  - EC2
  - Ansible
permalink: article/2020-03-16-aws-vpc-with-ansible
---

## Introduction

A critical first step to creating an
[Amazon Web Services (AWS)](https://aws.amazon.com/)
[AWS Elastic Compute Cloud (EC2)](https://aws.amazon.com/ec2/)
configuration is to configure a
[Virtual Private Cloud (VPC)](https://aws.amazon.com/vpc/).
Often, the default configuration is sufficient for most administrators'
needs but some solutions require an IP address space different than the
Amazon default.  This article presents the
[Ansible](https://www.ansible.com/) boilerplate for
configuring an alternative IP address space specified by a minimum of
parameters:
[region](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.RegionsAndAvailabilityZones.partial.html),
VPC CIDR block (e.g., 10.1.0.0/16) and subnet mask size (e.g., 20).  The
boilerplate calculates subnet CIDR blocks (e.g., 10.1.0.0/20, 10.1.16.0/20,
etc...) for each availability zone within the region.

The solution leverages Ansible's
[`ipaddr`](https://docs.ansible.com/ansible/latest/user_guide/playbooks_filters_ipaddr.html)
filter interface to the
[`netaddr`](https://pypi.org/project/netaddr/) Python package
with its
[extended loop variables](https://docs.ansible.com/ansible/latest/user_guide/playbooks_loops.html#extended-loop-variables).
The solution also makes extensive use of the Ansible
[JSON query filter](https://docs.ansible.com/ansible/latest/user_guide/playbooks_filters.html#json-query-filter)
to parse the results of the AWS modules.

## Theory of Operation

The implementation:

1. Requires the specification of:

    - AWS profile (for autheniticaion and use as a project-level name)
    - AWS region (where the VPC will be deployed)
    - VPC CIDR block
    - subnet mask size (in bits)

2. Creates the VPC in the specified region (with the same name as the
   profile for idempotent operation)

3. For each availability zone within the region, creates a unique subnet
   within the VPC's CIDR block of the specified size

4. Creates an
   [Internet Gateway](https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html)
   and connects to each of the subnets

The implementation also demonstrates how host IP address may be calculated
relative to an availability zone's subnet.

## Implementation

The Ansible controller must have the `netaddr` Python package installed.

``` bash
$ pip install --upgrade netaddr
```

In these examples the administrator has configured the AWS CLI
[environment variables](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-envvars.html)
`AWS_PROFILE` and `AWS_DEFAULT_REGION` to provide the necessary parameters.
The CIDR block and subnet mask size are also specified for the project as
Ansible facts.

{% raw %}
``` yaml
- name: Role Parameters
  set_fact:
    profile: "{{ lookup('env', 'AWS_PROFILE') }}"
    cidr_block: 10.1.0.0/16
    subnet_mask_size: 20

- name: aws_region_info
  aws_region_info:
    filters:
      region_name: "{{ lookup('env', 'AWS_DEFAULT_REGION') }}"
  register: aws_region_info
```
{% endraw %}

The `aws_region_info` module is invoked to verify the `AWS_DEFAULT_REGION`
parameter.

``` json
    "aws_region_info": {
        "changed": false,
        "failed": false,
        "regions": [
            {
                "endpoint": "ec2.us-west-1.amazonaws.com",
                "opt_in_status": "opt-in-not-required",
                "region_name": "us-west-1"
            }
        ]
    }
```

Once verified, the region is set as a fact for clarity and ease of reference
in subsequent module invocations.

{% raw %}
``` yaml
- name: region
  set_fact:
    region: "{{ aws_region_info.regions[0].region_name }}"
```
{% endraw %}

The VPC may be created with the above parameters.  Note that the
`AWS_PROFILE` value is used to name the VPC for idempotent operation.

{% raw %}
``` yaml
- name: "{{ profile }} VPC"
  ec2_vpc_net:
    name: "{{ profile }}"
    region: "{{ region }}"
    cidr_block: "{{ cidr_block }}"
  register: ec2_vpc_net
```
{% endraw %}

``` json
    "ec2_vpc_net": {
        "changed": true,
        "failed": false,
        "vpc": {
            "cidr_block": "10.1.0.0/16",
            "cidr_block_association_set": [
                {
                    "association_id": "vpc-cidr-assoc-ffffffffffffa4c3",
                    "cidr_block": "10.1.0.0/16",
                    "cidr_block_state": {
                        "state": "associated"
                    }
                }
            ],
            "classic_link_enabled": false,
            "dhcp_options_id": "dopt-ffffffffffff49b8",
            "id": "vpc-ffffffffffff9ace",
            "instance_tenancy": "default",
            "is_default": false,
            "owner_id": "999999999999",
            "state": "available",
            "tags": {
                "Name": "PROFILE"
            }
        }
    }
```

The `ec2_vpc_net.vpc` is set as a fact for ease of reference in subsequent
modules.

{% raw %}
``` yaml
- name: vpc
  set_fact:
    vpc: "{{ ec2_vpc_net.vpc }}"
```
{% endraw %}

The `aws_az_info` module is invoked to retrieve the availability zones for
the region:

{% raw %}
``` yaml
- name: aws_az_info
  aws_az_info:
    filters:
      region_name: "{{ aws_region_info.regions[0].region_name }}"
  register: aws_az_info
```
{% endraw %}

``` json
    "aws_az_info": {
        "availability_zones": [
            {
                "group_name": "us-west-1",
                "messages": [],
                "network_border_group": "us-west-1",
                "opt_in_status": "opt-in-not-required",
                "region_name": "us-west-1",
                "state": "available",
                "zone_id": "usw1-az1",
                "zone_name": "us-west-1a"
            },
            {
                "group_name": "us-west-1",
                "messages": [],
                "network_border_group": "us-west-1",
                "opt_in_status": "opt-in-not-required",
                "region_name": "us-west-1",
                "state": "available",
                "zone_id": "usw1-az3",
                "zone_name": "us-west-1c"
            }
        ],
        "changed": false,
        "failed": false
    }
```

The necessary parameters to create the subnets have been accumulated.  The
JSON query `availability_zones[].zone_name` is applied to the results of
`aws_az_info` resulting in the string array (`['us-west-1a', 'us-west-1c']`)
of availability zone names.  This array is iterated over to calculate the
subnet CIDR blocks based on `vpc.cidr_block`, subnet mask size, and the
array index (`ansible_loop.index0`).

{% raw %}
``` yaml
- name: "{{ profile }} VPC Subnets"
  vars:
    json: "{{ aws_az_info }}"
    query: "availability_zones[].zone_name"
    availability_zones: "{{ json | json_query(query) }}"
  ec2_vpc_subnet:
    vpc_id: "{{ vpc.id }}"
    az: "{{ item }}"
    cidr: >-
      {{ vpc.cidr_block | ipsubnet(subnet_mask_size, ansible_loop.index0) }}
  loop: "{{ availability_zones }}"
  loop_control:
    extended: yes

- name: ec2_vpc_subnet_info
  ec2_vpc_subnet_info:
    filters:
      vpc-id: "{{ vpc.id }}"
  register: ec2_vpc_subnet_info
```
{% endraw %}

The descriptions of the subnets are retrieved with `ec2_vpc_subnet_info`:

``` json
    "ec2_vpc_subnet_info": {
        "changed": false,
        "failed": false,
        "subnets": [
            {
                "assign_ipv6_address_on_creation": false,
                "availability_zone": "us-west-1c",
                "availability_zone_id": "usw1-az3",
                "available_ip_address_count": 4091,
                "cidr_block": "10.1.16.0/20",
                "default_for_az": false,
                "id": "subnet-ffffffffffffeb5e",
                "ipv6_cidr_block_association_set": [],
                "map_public_ip_on_launch": false,
                "owner_id": "999999999999",
                "state": "available",
                "subnet_arn": "arn:aws:ec2:us-west-1:999999999999:subnet/subnet-ffffffffffffeb5e",
                "subnet_id": "subnet-ffffffffffffeb5e",
                "tags": {},
                "vpc_id": "vpc-ffffffffffff9ace"
            },
            {
                "assign_ipv6_address_on_creation": false,
                "availability_zone": "us-west-1a",
                "availability_zone_id": "usw1-az1",
                "available_ip_address_count": 4091,
                "cidr_block": "10.1.0.0/20",
                "default_for_az": false,
                "id": "subnet-ffffffffffff57fd",
                "ipv6_cidr_block_association_set": [],
                "map_public_ip_on_launch": false,
                "owner_id": "999999999999",
                "state": "available",
                "subnet_arn": "arn:aws:ec2:us-west-1:999999999999:subnet/subnet-ffffffffffff57fd",
                "subnet_id": "subnet-ffffffffffff57fd",
                "tags": {},
                "vpc_id": "vpc-ffffffffffff9ace"
            }
        ]
    }
```

With the above information, an Internet Gateway may be created and added to
the route tables for the subnets.

{% raw %}
``` yaml
- name: "{{ profile }} VPC IGW"
  ec2_vpc_igw:
    vpc_id: "{{ vpc.id }}"
  register: ec2_vpc_igw

- name: "{{ profile }} VPC IGW Route Table"
  vars:
    json: "{{ ec2_vpc_subnet_info }}"
    query: "subnets[].id"
    subnets: "{{ json | json_query(query) }}"
  ec2_vpc_route_table:
    vpc_id: "{{ vpc.id }}"
    tags:
      Name: Internet
    subnets: "{{ subnets }}"
    routes:
      - dest: 0.0.0.0/0
        gateway_id: "{{ ec2_vpc_igw.gateway_id }}"
```
{% endraw %}

The following snippet demonstrates how a subnet may be found for an
availability zone and a host IP may be calculated relative to that subnet.

{% raw %}
``` yaml
- name: availability_zone
  set_fact:
    availability_zone: "{{ region }}a"

- name: "subnet ({{ availability_zone }})"
  vars:
    json: "{{ ec2_vpc_subnet_info }}"
    query: "subnets[?availability_zone=='{{ availability_zone }}'] | [0]"
    subnet: "{{ json | json_query(query) }}"
  set_fact:
    subnet: "{{ subnet }}"

- name: ENI
  vars:
    index: 4
    private_ip_address: "{{ subnet.cidr_block | ipmath(index) }}"
  ec2_eni:
    subnet_id: "{{ subnet.id }}"
    private_ip_address: "{{ private_ip_address }}"
  register: eni
```
{% endraw %}

## Summary

The Ansible boilerplate discussed herein can configure an AWS VPC with a
minimum of parameters specified by the administrator.

## Boilerplate

The complete boilerplate suitable for cut-and-paste is provide below.

{% raw %}
``` yaml
- name: Role Parameters
  set_fact:
    profile: "{{ lookup('env', 'AWS_PROFILE') }}"
    cidr_block: 10.1.0.0/16
    subnet_mask_size: 20

- name: aws_region_info
  aws_region_info:
    filters:
      region_name: "{{ lookup('env', 'AWS_DEFAULT_REGION') }}"
  register: aws_region_info

- name: aws_az_info
  aws_az_info:
    filters:
      region_name: "{{ aws_region_info.regions[0].region_name }}"
  register: aws_az_info

- name: region
  vars:
    region: "{{ aws_region_info.regions[0].region_name }}"
  set_fact:
    region: "{{ region }}"

- name: "{{ profile }} VPC"
  ec2_vpc_net:
    name: "{{ profile }}"
    region: "{{ region }}"
    cidr_block: "{{ cidr_block }}"
  register: ec2_vpc_net

- name: vpc
  vars:
    vpc: "{{ ec2_vpc_net.vpc }}"
  set_fact:
    vpc: "{{ vpc }}"

- name: "{{ profile }} VPC Subnets"
  vars:
    json: "{{ aws_az_info }}"
    query: "availability_zones[].zone_name"
    availability_zones: "{{ json | json_query(query) }}"
  ec2_vpc_subnet:
    vpc_id: "{{ vpc.id }}"
    az: "{{ item }}"
    cidr: >-
      {{ vpc.cidr_block | ipsubnet(subnet_mask_size, ansible_loop.index0) }}
  loop: "{{ availability_zones }}"
  loop_control:
    extended: yes

- name: ec2_vpc_subnet_info
  ec2_vpc_subnet_info:
    filters:
      vpc-id: "{{ vpc.id }}"
  register: ec2_vpc_subnet_info

- name: "{{ profile }} VPC IGW"
  ec2_vpc_igw:
    vpc_id: "{{ vpc.id }}"
  register: ec2_vpc_igw

- name: "{{ profile }} VPC IGW Route Table"
  vars:
    json: "{{ ec2_vpc_subnet_info }}"
    query: "subnets[].id"
    subnets: "{{ json | json_query(query) }}"
  ec2_vpc_route_table:
    vpc_id: "{{ vpc.id }}"
    tags:
      Name: Internet
    subnets: "{{ subnets }}"
    routes:
      - dest: 0.0.0.0/0
        gateway_id: "{{ ec2_vpc_igw.gateway_id }}"
```
{% endraw %}
