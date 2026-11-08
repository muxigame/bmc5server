import io,unittest
from unittest.mock import patch
import server


class ConsoleEncodingTests(unittest.TestCase):
    def test_gbk_log_replacement_cannot_abort_server_controller(self):
        raw=io.BytesIO()
        stream=io.TextIOWrapper(raw,encoding='gbk',errors='strict')
        with patch.object(server.sys,'stdout',stream):
            server.configure_console()
            print('mod log: \ufffd 中文',flush=True)
        self.assertIn(b'\\ufffd',raw.getvalue())
        self.assertIn('中文'.encode('gbk'),raw.getvalue())

if __name__=='__main__':unittest.main()
