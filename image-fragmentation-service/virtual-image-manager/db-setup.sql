create database virtualimages;
create user 'optimizer'@'localhost' IDENTIFIED BY 'entice';
grant all privileges on virtualimages.* TO 'optimizer'@'localhost';
flush privileges;