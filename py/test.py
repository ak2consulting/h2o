import os, json, unittest, time, shutil, sys
# not needed, but in case you move it down to subdir
sys.path.extend(['.','..'])
import h2o_cmd
import h2o
import h2o_browse as h2b

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=3)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_A_Basic(self):
        h2o.verify_cloud_size()

    def test_B_RF_iris2(self):
        h2o_cmd.runRF(trees=6, timeoutSecs=10,
                csvPathname = h2o.find_file('smalldata/iris/iris2.csv'))

    def test_C_RF_poker100(self):
        h2o_cmd.runRF(trees=6, timeoutSecs=10,
                csvPathname = h2o.find_file('smalldata/poker/poker100'))

    def test_D_GenParity1(self):
        trees = 50
        h2o_cmd.runRF(trees=50, timeoutSecs=15, 
                csvPathname = h2o.find_file('smalldata/parity_128_4_100_quad.data'))

    def test_E_ParseManyCols(self):
        csvPathname=h2o.find_file('smalldata/fail1_100x11000.csv.gz')
        parseKey = h2o_cmd.parseFile(None, csvPathname, timeoutSecs=10)
        # JSON is 1.7MB, takes forever to parse on Jython
        h2o.nodes[0].inspect_no_json(parseKey['destination_key'])

    def test_F_StoreView(self):
        storeView = h2o.nodes[0].store_view()


if __name__ == '__main__':
    h2o.unit_main()
