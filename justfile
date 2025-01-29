make-services:
  #!/usr/bin/env bash
  cd services
  npm clean-install
  npm run bundle

create-aws-cluster:
  #!/usr/bin/env bash
  cd aws
  npm clean-install
  npm run setup

update-aws-cluster:
  #!/usr/bin/env bash
  cd aws
  npm run deploy

destroy-aws-cluster:
  #!/usr/bin/env bash
  cd aws
  npm run destroy
