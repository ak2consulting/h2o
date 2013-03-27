from water import H2O, TestUtil
from water.sys import VM, RemoteRunner, NodeCL
from water.sys.Jython import browser
import h2o
import h2o_cmd
import h2o_hosts
import sys
import time

# h2o.build_cloud(node_count=3)

h2o.config_json = "pytest_config-cypof.json"
# h2o.config_json = "pytest_config-150-155.json"

ip = sys.argv[2].split(',')
ip[0] = '127.0.0.1'
print ip
h2o_hosts.build_cloud_with_hosts(ip=ip)

h2o_cmd.runKMeans(csvPathname='smalldata/covtype/covtype.20k.data', key='covtype', k=7)
# n = h2o.nodes[0]
# browser.open("http://" + n.node.address() + ":" + str(n.port) + "/Inspect.html?key=covtype")
# time.sleep(1)
# h2o.tear_down_cloud()
