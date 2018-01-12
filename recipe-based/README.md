# ENTICE WP3 Recipe based Image Synthesis Service #

This service allow the automated creation of VM images using pre-made descriptors (recipes).

The service relies on [Packer](http://packer.io), and for QEMU/KVM based images requires a physical host with KVM available.

The directories contain the following:

* _backend_ : Backend part of the service. The backend is responsible for building the requested images. This is a Python application.
* _deploy_ : Ansible playbooks for deploying the services (frontend and backend). Also a Vagrantfile is provided for testing.
* _docker_ : Docker-compose based deployment of the services (frontend and backend) with a shared data volume. Please not this is not intended for QEMU/KVM based builds.
* _examples_ : Example recipes (Occopus, Redmine and WordPress).
* _frontend_: The frontend part of the service. It provides a REST(-like) API for interacting with the backend. It is a Flask based Python application.

The service directories (_backend_ and _frontend_) contain additional README files for the REST API and configuration. The recommended way for deployment is using the Ansible playbooks or via docker-compose.

