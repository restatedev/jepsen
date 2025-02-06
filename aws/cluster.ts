import "source-map-support/register";
import * as cdk from "aws-cdk-lib";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as iam from "aws-cdk-lib/aws-iam";

const controlNodeSource = process.env.CONTROL_SOURCE_CIDR ?? "0.0.0.0/0"; // source will be allowed full network access including SSH
const nodes = 3;
const instanceType = ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MICRO);

// --- no configuration past this point ---

const app = new cdk.App();
const stack = new cdk.Stack(app, `restate-jepsen-cluster-${process.env.ENV_SUFFIX ?? process.env.USER}`, {
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
securityGroup.addIngressRule(securityGroup, ec2.Port.allTraffic());
securityGroup.addIngressRule(ec2.Peer.ipv4(controlNodeSource), ec2.Port.allTraffic());

const keyPair = new ec2.KeyPair(stack, "SshKeypair");
const instanceRole = new iam.Role(stack, "InstanceRole", {
  assumedBy: new iam.ServicePrincipal("ec2.amazonaws.com"),
  managedPolicies: [iam.ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")],
});
const instanceProfile = new iam.InstanceProfile(stack, "InstanceProfile", { role: instanceRole });

function addNodeInstance(n: number) {
  const cloudConfig = ec2.UserData.custom([`cloud_final_modules:`, `- [scripts-user, once]`].join("\n"));

  const initScript = ec2.UserData.forLinux();
  initScript.addCommands(`hostnamectl set-hostname n${n}`);

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
