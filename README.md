# jepsen.restate

A Clojure library designed to ... well, that part is up to you.

## Requirements

* Clojure
* JVM
* Vagrant
* Just
* gnuplot
* A valid access token for Github - place that in `vagrant/gh_token.json`

## Building and publish the service

* Goto `service/` directory
* Build the `Docker` image
* Push the image to our private container registry

```bash
docker images # select the jepsen service image id
docker tag $theimageid ghcr.io/restatedev/jepsen:latest
docker push ghcr.io/restatedev/jepsen:latest
```

## VM management using Vagrant

* At the first time starting the VM you need to provison them (install and auth Docker)

```bash
just vm up --provision
```

* Check their status

```bash
just vm status
```

* To stop them

```bash
just vm halt
```

* After the first time, to start the VMs use:

```bash
just vm up 
```

* If you would like to ssh to an individual machine, for example n1 use:

```bash
just ssh n1
```

## Run

* make sure that the VMs are running

```bash
just vm-status
```

* run the test

```bash
just test
```

You should see:

```
Everything looks good! ヽ(‘ー`)ノ
```

