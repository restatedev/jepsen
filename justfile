make-services:
	#!/usr/bin/env bash
	cd services
	npm clean-install
	npm run bundle
