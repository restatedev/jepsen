# jepsen.restate

A Clojure library designed to ... well, that part is up to you.

## Requirnments

* Clojure
* JVM
* Vagrant
* Just
* A valid access token for Github - place that in `vagrant/gh_token.json`

## VM management using Vagrant 

* To start 5 machines, named n1, n2, n3, n4, n5

```bash
just vm up 
```

* Check their status

```bash
just vm status
```

* To stop them
```bash
just vm halt
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

