vm *command:
	#!/usr/bin/env bash
	cd vagrant
	vagrant {{command}}

ssh node:
	#!/usr/bin/env bash
	cd vagrant
	vagrant ssh {{node}}


make-service:
	#!/usr/bin/env bash
	cd service
	docker build . -t jepsen
	cd ..


test *flags:
	#!/usr/bin/env bash
	lein run -- test --concurrency 100 {{flags}} --nodes-file ./vagrant/nodes.txt 


