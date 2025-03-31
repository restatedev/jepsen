/*
 * Copyright (c) 2023-2025 - Restate Software, Inc., Restate GmbH
 *
 * This file is part of the Restate Jepsen test suite,
 * which is released under the MIT license.
 *
 * You can find a copy of the license in file LICENSE in the root
 * directory of this repository or package, or at
 * https://github.com/restatedev/jepsen/blob/main/LICENSE
 */

import * as cdk from "aws-cdk-lib";
import { aws_ec2 as ec2, aws_iam as iam, aws_s3 as s3 } from "aws-cdk-lib";

const app = new cdk.App();

const stackName =
  app.node.tryGetContext("stack-name") || `restate-jepsen-cluster-${process.env.ENV_SUFFIX ?? process.env.USER}`;
const controlNodeSource = app.node.tryGetContext("allow-source-cidr") ?? "0.0.0.0/0";
const nodes = 3;
const instanceType = ec2.InstanceType.of(ec2.InstanceClass.T4G, ec2.InstanceSize.SMALL);
// if you have existing buckets, pass their names into the stack and the workers will be granted access;
// if unset, unique buckets will be created as part of deploying the stack
const bucketName = app.node.tryGetContext("bucket-name");

// --- no configuration past this point ---

const stack = new cdk.Stack(app, stackName, {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION,
  },
});

const vpc = ec2.Vpc.fromLookup(stack, "Vpc", { isDefault: true });

const securityGroup = new ec2.SecurityGroup(stack, "SecurityGroup", {
  vpc,
  description: "Restate Jepsen cluster",
});
securityGroup.connections.allowInternally(ec2.Port.allTraffic());
securityGroup.connections.allowToAnyIpv4(ec2.Port.SSH);
securityGroup.addIngressRule(ec2.Peer.ipv4(controlNodeSource), ec2.Port.allTraffic());

const keyPair = new ec2.KeyPair(stack, "SshKeypair");
const instanceRole = new iam.Role(stack, "InstanceRole", {
  assumedBy: new iam.ServicePrincipal("ec2.amazonaws.com"),
  managedPolicies: [iam.ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")],
});
const instanceProfile = new iam.InstanceProfile(stack, "InstanceProfile", { role: instanceRole });

let bucket: s3.IBucket;
if (bucketName) {
  bucket = s3.Bucket.fromBucketName(stack, "ClusterBucket", bucketName);
} else {
  bucket = new s3.Bucket(stack, "ClusterBucket", {
    autoDeleteObjects: true,
    removalPolicy: cdk.RemovalPolicy.DESTROY,
  });
}
bucket.grantReadWrite(instanceRole);
new cdk.CfnOutput(stack, `BucketName`, { value: bucket.bucketName });

function addNodeInstance(n: number) {
  const cloudConfig = ec2.UserData.custom([`cloud_final_modules:`, `- [scripts-user, once]`].join("\n"));

  const initScript = ec2.UserData.forLinux();
  initScript.addCommands(`hostnamectl set-hostname n${n}`);
  initScript.addCommands(`if ! dpkg -l amazon-ssm-agent > /dev/null 2>&1; then
    while fuser /var/lib/dpkg/lock /var/lib/apt/lists/lock /var/cache/apt/archives/lock >/dev/null 2>&1; do
      echo "Waiting for dpkg lock..."
      sleep 5
    done

    wget https://s3.amazonaws.com/ec2-downloads-windows/SSMAgent/latest/debian_amd64/amazon-ssm-agent.deb
    dpkg -i amazon-ssm-agent.deb
    rm amazon-ssm-agent.deb
  fi
  `);
  const userData = new ec2.MultipartUserData();
  userData.addUserDataPart(cloudConfig, "text/cloud-config");
  userData.addUserDataPart(initScript, "text/x-shellscript");

  const node = new ec2.Instance(stack, `N${n}`, {
    vpc,
    vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
    instanceType,
    machineImage: ec2.MachineImage.fromSsmParameter(
      "/aws/service/debian/release/bookworm/latest/" + instanceType.architecture.replace("x86_64", "amd64"),
    ),
    instanceProfile,
    keyPair,
    blockDevices: [
      {
        deviceName: "/dev/xvda", // root device for Debian AMI; may differ if you change machine image
        volume: ec2.BlockDeviceVolume.ebs(16),
      },
    ],
    userData,
  });
  node.addSecurityGroup(securityGroup);

  new cdk.CfnOutput(stack, `InstanceId${n}`, { value: node.instanceId });
  new cdk.CfnOutput(stack, `Node${n}`, { value: node.instancePublicDnsName });
}

for (let n = 1; n <= nodes; n++) {
  addNodeInstance(n);
}

new cdk.CfnOutput(stack, "KeyArn", { value: keyPair.privateKey.parameterArn });

cdk.Tags.of(stack).add("purpose", "jepsen-tests");
cdk.Tags.of(stack).add("stack", stackName);
