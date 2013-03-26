from water.sys import EC2, Host, RemoteRunner
from water.sys.Jython import browser
import h2o
import h2o_cmd
import h2o_hosts
import time

ec2 = EC2.resize(2, 'm1.xlarge', 'us-east-1')

# Sync code and tests to a master machine 
master = Host(ec2.publicIPs()[0])
includes = Host.defaultIncludes().tolist() + [ 'py', 'smalldata', 'AwsCredentials.properties' ]
excludes = Host.defaultExcludes().tolist() + [ 'py/**.class', '**/cachedir', '**/sandbox' ]
master.rsync(includes, excludes);

# Run test
hosts = ','.join(ec2.privateIPs())
RemoteRunner.launch(master, [ '-mainClass', 'water.sys.Jython', 'py/cypof.py', '-hosts', hosts, '--headers' ]);

input("Press Enter to continue...\n")
browser.open("http://localhost:54321/Inspect.html?key=covtype")

input("Press Enter to continue...\n")
h2o.tear_down_cloud()
