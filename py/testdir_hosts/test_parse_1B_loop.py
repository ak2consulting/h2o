import unittest, sys, random, time
sys.path.extend(['.','..','py'])
import h2o, h2o_cmd, h2o_browse as h2b, h2o_import as h2i, h2o_hosts

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        pass
        print "Will build clouds with incrementing heap sizes and import folder/parse"

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_import_billion_rows_parse_loop(self):
        print "Apparently we can't handle 1B rows .gzed"
        csvFilename = "billion_rows.csv.gz"
        importFolderPath = "/home/0xdiag/datasets"
        trialMax = 3
        for tryHeap in [4,16]:
            print "\n", tryHeap,"GB heap, 1 jvm per host, import folder,", \
                "then loop parsing 'billion_rows.csv' to unique keys"
            h2o_hosts.build_cloud_with_hosts(node_count=1, java_heap_GB=tryHeap)
            timeoutSecs=800
            for trial in range(trialMax):
                # since we delete the key, we have to re-import every iteration, to get it again
                h2i.setupImportFolder(None, importFolderPath)

                key2 = csvFilename + "_" + str(trial) + ".hex"
                start = time.time()
                parseKey = h2i.parseImportFolderFile(None, csvFilename, importFolderPath, key2=key2, 
                    timeoutSecs=timeoutSecs, retryDelaySecs=4, pollTimeoutSecs=60)
                elapsed = time.time() - start
                print "Trial #", trial, "completed in", elapsed, "seconds.", \
                    "%d pct. of timeout" % ((elapsed*100)/timeoutSecs)

                print "Deleting key in H2O so we get it from S3 (if ec2) or nfs again. ", \
                      "Otherwise it would just parse the cached key."
                storeView = h2o.nodes[0].store_view()
                ### print "storeView:", h2o.dump_json(storeView)
                print "Removing", parseKey['source_key']
                removeKeyResult = h2o.nodes[0].remove_key(key=parseKey['source_key'])
                ### print "removeKeyResult:", h2o.dump_json(removeKeyResult)

            # sticky ports?
            h2o.tear_down_cloud()
            time.sleep(5)

if __name__ == '__main__':
    h2o.unit_main()
