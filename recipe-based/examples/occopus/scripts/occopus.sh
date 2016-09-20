PACKAGES="
python
python-dev
libffi-dev
python-virtualenv
redis-server
libssl-dev
mysql-client
"
apt-get -y install $PACKAGES
su - ubuntu -c "
virtualenv occopus;
source occopus/bin/activate;
pip install --upgrade pip;
pip install --find-links http://pip.lpds.sztaki.hu/packages --no-index --trusted-host pip.lpds.sztaki.hu OCCO-API;
mkdir -p $HOME/.occopus;
curl https://raw.githubusercontent.com/occopus/docs/devel/tutorial/.occopus/occopus_config.yaml -o $HOME/.occopus/occopus_config.yaml;
curl https://raw.githubusercontent.com/occopus/docs/devel/tutorial/.occopus/redis_config.yaml -o $HOME/.occopus/redis_config.yaml;
curl https://raw.githubusercontent.com/occopus/docs/devel/tutorial/.occopus/auth_data.yaml -o $HOME/.occopus/auth_data.yaml;"
