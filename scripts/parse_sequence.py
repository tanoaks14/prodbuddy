import sys
import re

def parse_logs(log_file):
    # Matches: [SEQUENCE_TRACE] sender="Sender" receiver="Receiver" method="Method" action="Action"
    # Added \s* to securely accommodate PowerShell truncating stderr onto sequential strings.
    pattern = re.compile(r'\[SEQUENCE_TRACE\]\s*sender="([^"]+)"\s*receiver="([^"]+)"\s*method="([^"]+)"\s*action="([^"]+)"')
    
    events = []
    
    # Auto-detect encoding based on BOM or null bytes
    encoding = 'utf-8'
    with open(log_file, 'rb') as f:
        head = f.read(4)
        if head.startswith(b'\xff\xfe') or head.startswith(b'\xfe\xff') or b'\x00' in head:
            encoding = 'utf-16'

    with open(log_file, 'r', encoding=encoding) as f:
        content = f.read()
        
        # Strip structural newlines out so regex can traverse contiguous attributes linearly.
        normalized = content.replace('\n', ' ').replace('\r', ' ')
        
        for match in pattern.finditer(normalized):
            sender, receiver, method, action = match.groups()
            events.append((sender, receiver, method, action))
    
    return events

def generate_mermaid(events, output_file):
    with open(output_file, 'w') as f:
        f.write("```mermaid\n")
        f.write("sequenceDiagram\n")
        f.write("    autonumber\n")
        
        for sender, receiver, method, action in events:
            # actor ->> target : [method] Action
            # e.g., Client ->> Orchestrator: [run] Started Orchestration
            f.write(f"    {sender} ->> {receiver}: [{method}] {action}\n")
            
        f.write("```\n")

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: python parse_sequence.py <input.log> <output.md>")
        sys.exit(1)
        
    input_log = sys.argv[1]
    output_md = sys.argv[2]
    
    parsed_events = parse_logs(input_log)
    generate_mermaid(parsed_events, output_md)
    print(f"Generated sequence diagram at {output_md} with {len(parsed_events)} events.")
