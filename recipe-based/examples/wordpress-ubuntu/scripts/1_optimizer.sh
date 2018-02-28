#!/bin/bash -eux

# Add optimizer temp key... :-(
mkdir -p /root/.ssh
chmod 700 /root/.ssh
echo 'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDOxwuKuMXkhuUfTmJxBBiR5UbsHJpWpedS88fvTy6/y60UyDyrd+A6qR/9Ef/UHQe9x8YOSXPodBUr5eHTtG5sfPcu+j9HactVvttGBzPVvXQZHvu5sUsXNzGU9kka/H0uVpbJDnT955K3JO3uV7BBtmqRCAPGiNfaZsq6Tx2Wq1fHpqBIxr6NPyRZ6PG/IXjaNhMwk4KABxqWS/ONuyIQuRc1agmjCs/dTBoO7y0SKi5DrjCyMgGlHqPLHKN6icREXI5/9ztkL+KiD022p1+onxq4n/gtujVolvOXm8EHLSCCmxPPfvF3cyPI8ox+ZECr/wPk0HEgqG19dIAt0H4t' > /root/.ssh/authorized_keys
chmod 600 /root/.ssh/authorized_keys
