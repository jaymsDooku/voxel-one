import unittest
from check_packages import contexts_unavailable

class ContextQualificationTest(unittest.TestCase):
    def report(self, success=None):
        return '\n'.join(f'Context diagnosis: variant={i} created={"true" if i==success else "false"} code=0x{"0" if i==success else "10009"}' for i in range(4))
    def test_complete_failure_is_pending(self):
        self.assertTrue(contexts_unavailable(self.report()))
        self.assertTrue(contexts_unavailable(self.report().replace('\n','\r\n')))
    def test_any_created_context_requires_real_render(self):
        for i in range(4): self.assertFalse(contexts_unavailable(self.report(i)))
    def test_incomplete_or_ambiguous_probe_cannot_skip_render(self):
        for text in ['',self.report().split('\n')[0],self.report()+'\n'+self.report(),self.report().replace('variant=3','variant=2'),self.report().replace('10009','0')]:
            self.assertFalse(contexts_unavailable(text))
