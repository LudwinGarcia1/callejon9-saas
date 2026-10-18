// Publishes the inbound secret of the local agent so its systemd service can connect.
import jenkins.model.Jenkins

def computer = Jenkins.get().getComputer('local-agent')
if (computer == null) {
    throw new IllegalStateException('Node local-agent is not defined; check casc/jenkins.yaml')
}

new File('/run/jenkins-agent/secret').text = computer.jnlpMac
