# ENTICE WP3 / Image Synthesis -- Wordpress #

This is an example Packer recipe using CentOS 7 and Chef for Wordpress. 

## Requirements ##
- Packer 1.1.0+

## Creating the disk image manually with Packer ##
```
./getcookbooks.sh
./build.sh
```

## Running the disk image ##

### Start via QEMU ###

```
qemu-system-x86_64 -device virtio-net,netdev=user.0 -vnc 0.0.0.0:47 -m 512M -display sdl -drive file=tdhtest,if=virtio,cache=writeback,discard=unmap -name tdhtest -machine type=pc,accel=kvm -netdev user,id=user.0,hostfwd=tcp::2224-:22,hostfwd=tcp::8080-:80
```

### SSH to the VM ###

```
ssh root@localhost 2224
```

### Accessing Wordpress ###
Wordpress is running on port 80 in the VM. You need to finish the installation steps, navigate to:

http://<VM_IP>/wp-admin/install.php

## Credentials ##
1. SSH: root/alma


