#!/usr/bin/env zx
import { $ } from 'zx';

const n1 = "ec2-13-247-65-8.af-south-1.compute.amazonaws.com";
const n2 = "ec2-13-247-214-245.af-south-1.compute.amazonaws.com";
const n3 = "ec2-13-247-88-53.af-south-1.compute.amazonaws.com";


while (true) {
    // parallel
    let res = await Promise.all([
        $`curl --silent -m 0.25 http://${n1}:8080/Set/jepsen-set/get -o /dev/null`,
        $`curl --silent -m 0.25 http://${n2}:8080/Set/jepsen-set/get -o /dev/null`,
        $`curl --silent -m 0.25 http://${n3}:8080/Set/jepsen-set/get -o /dev/null`,
    ]);
    res.map((r) => {
        if (r.exitCode != 0) {
            return "✅";
        } else {
            return "❌";
        }
    });
    console.log(res.join(" "));
}
