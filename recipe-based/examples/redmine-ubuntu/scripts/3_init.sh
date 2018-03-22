#!/bin/bash

export DEBIAN_FRONTEND=noninteractive

systemctl start mysql

cat <<EOF | mysql -u root
CREATE DATABASE redmine_db CHARACTER SET utf8;
GRANT ALL PRIVILEGES ON redmine_db.* TO 'redmine_user'@'localhost' IDENTIFIED BY 'redminepwd';
FLUSH PRIVILEGES;
EOF

#sudo -u redmine -i bash -c "cd /opt/redmine-3.2.0 && bundle install --without development test"
sudo -u redmine -i bash -c "cd /opt/redmine-3.2.0 && bundle exec rake generate_secret_token"
sudo -u redmine -i bash -c "cd /opt/redmine-3.2.0 && RAILS_ENV=production bundle exec rake db:migrate"

systemctl enable redmine
systemctl start redmine
