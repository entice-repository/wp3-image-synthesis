#!/bin/bash
#set -eu
#set -xv

export DEBIAN_FRONTEND=noninteractive

#
# 1_mysql
#yum install -y http://dev.mysql.com/get/mysql-community-release-el7-5.noarch.rpm
#yum install -y mysql-community-server mysql-community-devel.x86_64
# Install MySQL.
apt -y install \
    mysql-server \
    mysql-client

cat <<EOF >/etc/my.cnf
# For advice on how to change settings please see
# http://dev.mysql.com/doc/refman/5.6/en/server-configuration-defaults.html

[mysqld]
datadir=/var/lib/mysql
socket=/var/lib/mysql/mysql.sock
bind-address=0.0.0.0


# Disabling symbolic-links is recommended to prevent assorted security risks
symbolic-links=0

# Recommended in standard MySQL setup
sql_mode=NO_ENGINE_SUBSTITUTION,STRICT_TRANS_TABLES

[mysqld_safe]
log-error=/var/log/mysqld.log
pid-file=/var/run/mysqld/mysqld.pid

EOF


#
# 3_redmine.sh


#
# Install packages
#
#systemctl start mysqld

# Ensure MySQL is not running
service mysql stop

#cat <<EOF | mysql -u root
#CREATE DATABASE redmine_db CHARACTER SET utf8;
#GRANT ALL PRIVILEGES ON redmine_db.* TO 'redmine_user'@'localhost' IDENTIFIED BY 'redminepwd';
#FLUSH PRIVILEGES;
#EOF

#Download and Install Redmine 3.2.0
#Install packages
apt -y install \
    git-svn \
    curl \
    libssl-dev \
    libreadline-dev \
    libyaml-dev \
    libsqlite3-dev \
    libxml2-dev \
    libxslt1-dev \
    libcurl4-openssl-dev \
    python \
    libffi-dev \
    libmagick++-dev \
    libmysqlclient-dev \
    gcc

WORKDIR=/tmp
cd ${WORKDIR}

mkdir -p /opt
wget http://www.redmine.org/releases/redmine-3.2.0.tar.gz -O /opt/redmine-3.2.0.tar.gz
cd /opt
tar zxvf /opt/redmine-3.2.0.tar.gz

cd ${WORKDIR}

cat <<EOF>/opt/redmine-3.2.0/config/database.yml
production:
  adapter: mysql2
  database: redmine_db
  host: localhost
  username: redmine_user
  password: "redminepwd"
  encoding: utf8
EOF

useradd redmine -d /home/redmine -m
chown -R redmine /opt/redmine-3.2.0

cd /home/redmine/


git clone https://github.com/rbenv/rbenv.git /home/redmine/.rbenv
echo 'export PATH="$HOME/.rbenv/bin:$PATH"' >> /home/redmine/.bash_profile
echo 'export PATH="$HOME/.rbenv/bin:$PATH"' >> /home/redmine/.profile
export PATH="/home/redmine/.rbenv/bin:$PATH"
echo 'eval "$(rbenv init -)"' >> /home/redmine/.bash_profile
echo 'eval "$(rbenv init -)"' >> /home/redmine/.profile
eval "$(rbenv init -)"


git clone https://github.com/rbenv/ruby-build.git /home/redmine/.rbenv/plugins/ruby-build
echo 'export PATH="$HOME/.rbenv/plugins/ruby-build/bin:$PATH"' >> /home/redmine/.bash_profile
echo 'export PATH="$HOME/.rbenv/plugins/ruby-build/bin:$PATH"' >> /home/redmine/.profile

export PATH="/home/redmine/.rbenv/plugins/ruby-build/bin:$PATH"
export PATH="/home/redmine/.rbenv/bin:$PATH"

chown -R redmine:redmine  /home/redmine/.rbenv

sudo -u redmine -i bash -c "rbenv install 2.2.3"
sudo -u redmine -i bash -c "rbenv global 2.2.3"

echo "gem: --no-ri --no-rdoc" > /home/redmine/.gemrc
chown redmine:redmine /home/redmine/.gemrc

sudo -u redmine -i gem install mysql2 -v '0.3.20' -s http://rubygems.org
sudo -u redmine -i gem install bundler --no-ri --no-rdoc -s http://rubygems.org

chown -R redmine /opt/redmine-3.2.0

sudo -u redmine -i bash -c "cd /opt/redmine-3.2.0 && bundle install --without development test"
#sudo -u redmine -i bash -c "cd /opt/redmine-3.2.0 && bundle exec rake generate_secret_token"
#sudo -u redmine -i bash -c "cd /opt/redmine-3.2.0 && RAILS_ENV=production bundle exec rake db:migrate"

cat <<EOF >/etc/systemd/system/redmine.service
[Unit]
Description=redmine
After=network.target
Requires=mysql.service
After=mysql.service

[Service]
ExecStart=/home/redmine/.rbenv/versions/2.2.3/bin/ruby /opt/redmine-3.2.0/bin/rails server webrick -e production -b 0.0.0.0
Restart=always
RestartSec=0

[Install]
WantedBy=multi-user.target
EOF

#systemctl enable redmine
#systemctl stop redmine
#systemctl stop mysqld
