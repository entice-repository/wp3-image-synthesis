# ENTICE WP3 / Image Synthesis #

Packer.io + Chef based image synthesis.

Work-in-progress.


## Creating the disk image ##
```
./getcookbooks.sh
./build.sh
```

## Running the disk image ##

### Start via QEMU ###

```
qemu-system-x86_64 -device virtio-net,netdev=user.0 -vnc 0.0.0.0:47 -m 512M -display sdl -drive file=tdhtest,if=virtio,cache=writeback,discard=unmap -name tdhtest -machine type=pc,accel=kvm -netdev user,id=user.0,hostfwd=tcp::2224-:22
```

### SSH to the VM ###

```
ssh root@localhost 2224
```

### Accessing Redmine ###
Redmine is running on port 80 in the VM.

## Credentials ##
1. SSH: root/alma
2. Redmine: admin/admin

