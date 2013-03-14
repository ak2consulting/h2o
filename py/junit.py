import os, json, unittest, time, shutil, sys, getpass
import h2o

class JUnit(unittest.TestCase):
    def testScoring(self):
        node = h2o.spawn_clone(None, [
                '-mainClass', 'org.junit.runner.JUnitCore',
                # The tests
                'water.score.ScorePmmlTest',
                'water.score.ScoreTest',
                ])

        rc = node.waitFor()
        if rc != 0:
            raise Exception("junit failed")

    def testAll(self):
        try:
            h2o.build_cloud(node_count=2)

            node = h2o.spawn_clone([
                    '-Dh2o.arg.ice_root=' + h2o.tmp_dir('ice.'),
                    '-Dh2o.arg.name=' + h2o.cloud_name(),
                    '-Dh2o.arg.ip=' + h2o.get_ip_address(),
                    ], [
                    '-mainClass', 'org.junit.runner.JUnitCore',
                    # The tests
                    #'hex.GLMGridTest',
                    'hex.GLMTest',
                    'hex.KMeansTest',
                    'hex.MinorityClassTest',
                    'hex.rf.RandomForestTest',
                    'hex.rf.RFPredDomainTest',
                    'water.AtomicTest',
                    'water.AutoSerialTest',
                    'water.ConcurrentKeyTest',
                    'water.KVTest',
                    'water.KeyToString',
                    'water.api.RStringTest',
                    'water.exec.ExprTest',
                    'water.exec.RBigDataTest',
                    'water.parser.DatasetCornerCasesTest',
                    'water.parser.ParseProgressTest',
                    'water.parser.ParserTest',
                    'water.parser.RReaderTest',
                    ])

            rc = node.waitFor()
            if rc != 0:
                raise Exception("junit failed")

        finally:
            h2o.tear_down_cloud()


    # def testMore(self):
    #        (ps, stdout, stderr) = h2o.spawn_cmd('junit', [
    #                'java',
    #                '-ea', '-jar', h2o.find_file('target/h2o.jar'),
    #                '-mainClass', 'org.junit.runner.JUnitCore',
    #                'hex.rf.RFRunner',
    #                ])
    #
    #        rc = ps.wait(None)
    #        out = file(stdout).read()
    #        err = file(stderr).read()
    #        if rc is None:
    #            ps.terminate()
    #            raise Exception("junit timed out.\nstdout:\n%s\n\nstderr:\n%s" % (out, err))
    #        elif rc != 0:
    #            raise Exception("junit failed.\nstdout:\n%s\n\nstderr:\n%s" % (out, err))



if __name__ == '__main__':
    h2o.unit_main()
