#!/usr/bin/env ruby

require 'json'
require 'yaml'

# How to use the script: https://gitlab.iut-clermont.uca.fr/auverlynde/iot-mock/wikis/rule-generator

# A rule is an object which is gonna be generated by the generator and used to mock
# a connected object behaviour. There are 2 types of rule: "outin" and "inout".
# A rule contains a type, a request(with its method, path, headers and body) and
# a response(with its status, headers and body).
class Rule
  attr_reader :rule_type
  attr_reader :request_method, :request_path, :request_headers, :request_body
  attr_reader :response_status, :response_headers, :response_body
  attr_accessor :rule_hash

  # Rule constructor.
  def initialize(rule_type, request_method, request_path, request_headers, request_body, response_status, response_headers, response_body)
    @rule_type = rule_type
    @request_method = request_method
    @request_path = request_path
    @request_headers = request_headers
    @request_body = request_body
    @response_status = response_status
    @response_headers = response_headers
    @response_body = response_body
  end

  # This method compares 2 rules to know if they are same or not.
  def equals?(rule)
    rule.instance_variables.each { |k| return false unless rule.instance_variable_get(k) == self.instance_variable_get(k) }
    true
  end

  # This method creates a hash from the rule request and response.
  # The hash will be converted as JSON or YAML.
  def rule_to_hash
    @rule_hash = {
      'type' => @rule_type,
      'request' => {
        'method' => @request_method,
        'path' => @request_path
      }
    }
    unless request_headers.nil?
      @rule_hash['request'].merge!('headers' => {})
      @rule_hash['request']['headers'].merge!(@request_headers)
    end
    unless request_body.nil? || request_body.empty?
      @rule_hash['request'].merge!('body' => @request_body)
    end
    @rule_hash.merge!('response' => {})
    @rule_hash['response'].merge!('status' => @response_status.to_i)
    unless response_headers.nil?
      @rule_hash['response'].merge!('headers' => {})
      @rule_hash['response']['headers'].merge!(@response_headers)
    end
    unless response_body.nil? || response_body.empty?
      @rule_hash['response'].merge!('body' => @response_body)
    end
    @rule_hash
  end
end

# The generator is initialized with a file name, a file type and the IP address
# of the connected object to mock. It reads the file and extract all the HTTP
# responses and requests and their content.
class Generator
  attr_reader :file_name, :file_type
  attr_accessor :file_data, :host, :paths, :rules
  attr_accessor :request_host, :request_dest, :request_method, :request_path, :request_headers, :request_body
  attr_accessor :response_host, :response_dest, :response_status, :response_headers, :response_body

  # Generator constructor.
  def initialize(file_name, file_type, host)
    @file_name = file_name
    @file_type = file_type
    @host = host
  end

  # This method reads the given file and extracts all IP addresses.
  def read_file(path)
    @file_data = File.open(path).readlines.map(&:chomp)
    @paths = []
    @file_data.each do |line|
      host_address = line.match(/Source=(([0-9]{1,3}\.)+[0-9]{1,3})/)
      dest_address = line.match(/Dest=(([0-9]{1,3}\.)+[0-9]{1,3})/)
      @paths << host_address[1] unless paths.include?(host_address[1])
      @paths << dest_address[1] unless paths.include?(dest_address[1])
    end
  end

  # This method shows the list of all IP addresses extracted from the file
  # and asks the user to choose the one of the connected object to mock.
  def ask_hostaddress
    puts 'Choose the host address: '
    @paths.each { |line| puts line }
    puts ''
    @host = $stdin.gets.chomp
    until paths.include?(host)
      puts "Error: the chosen address doesn't exist.\nChoose the host address: "
      paths.each { |line| puts line }
      puts ''
      @host = $stdin.gets.chomp
      puts ''
    end
    puts "=> #{@host} has been chosen as host address."
  end

  # This method extracts the content of a request(label, host address, dest address,
  # verb, path, headers and body).
  def get_request_composition(request)
    request = request.split('@')
    @request_label = request[0][/^.*\(/].tr('(', '').strip
    request[0][/^.*\(/] = ''
    request[request.length - 1][/\)/] = ''
    key_value = {}
    request.each do |val|
      if val.split('=')[0] == 'Uri'
        key_value['Uri'] = val.split('Uri=')[1]
      else
        vals = val.split('=')
        vals.delete_at(0)
        key_value[val.split('=')[0]] = vals.join('=')
      end
    end
    @request_host = key_value['Source'].strip
    key_value.delete('Source')
    @request_dest = key_value['Dest'].strip
    key_value.delete('Dest')
    @request_method = key_value['Verb'].strip
    key_value.delete('Verb')
    @request_path = key_value['Uri'].strip
    key_value.delete('Uri')
    @request_headers = {}
    @request_body = nil
    until key_value.empty?
      if key_value.keys[0] == 'contents'
        @request_body = key_value.values[0].strip
      else
        @request_headers[key_value.keys[0].strip] = key_value.values[0].strip
      end
      key_value.delete(key_value.keys[0].strip)
    end
    @request_headers = nil if @request_headers.empty?
  end

  # This method extracts the content of a response(label, host address, dest address,
  # status, headers and body).
  def get_response_composition(response)
    response = response.split('@')
    @response_label = response[0][/^.*\(/].tr('(', '').strip
    response[0][/^.*\(/] = ''
    response[response.length - 1][/\)/] = ''
    key_value = {}
    response.each do |val|
      if val.split('=')[0] == 'contents'
        key_value['contents'] = val.split('contents=')[1]
      else
        vals = val.split('=')
        vals.delete_at(0)
        key_value[val.split('=')[0]] = vals.join('=')
      end
    end
    @response_host = key_value['Source'].strip
    key_value.delete('Source')
    @response_dest = key_value['Dest'].strip
    key_value.delete('Dest')
    @response_status = key_value['status'].strip
    key_value.delete('status')
    @response_response = key_value['response'].strip unless key_value['response'].nil?
    key_value.delete('response')
    @response_headers = {}
    @response_body = nil
    until key_value.empty?
      if key_value.keys[0] == 'contents'
        @response_body = key_value.values[0].strip
      else
        @response_headers[key_value.keys[0].strip] = key_value.values[0].strip
      end
      key_value.delete(key_value.keys[0])
    end
    @response_headers = nil if @response_headers.empty?
  end

  # This method creates the rules file and writes them into it according the
  # requested format(JSON or YAML).
  def save_rule
    rules_hash = []
    @rules.each do |rule|
      rules_hash << rule.rule_to_hash
    end
    File.open("#{@file_name}.#{@file_type}", 'w') do |file|
      if @file_type == 'json'
        file.puts JSON.pretty_generate(rules_hash)
      else
        file.write(rules_hash.to_yaml)
      end
    end
  end

  # This method first distributes the read line into the correct list according
  # to if it's a request or a response. After this, it separates the content
  # of each request/response and then creates the rules according to their type.
  def create_rules
    requests = []
    responses = []
    @file_data.each do |line|
      if line.include?('Verb=')
        requests << line
      elsif line.include?('status=')
        responses << line
      else
        puts 'Error when dispatching resquests/responses.'
        exit
      end
    end
    @rules = []
    while !requests.empty? && !responses.empty?
      get_request_composition(requests[0])
      get_response_composition(responses[0])
      if @request_dest == @host && @response_host == @host
        @rule_type = 'inout'
      elsif @request_host == @host
        @rule_type = 'outin'
      else
        @rule_type = ''
      end
      if @rule_type == 'outin'
        fullpath = "http://#{@request_dest}#{request_path}"
        rule = Rule.new(@rule_type, @request_method, fullpath, @request_headers, @request_body, @response_status, @response_headers, @response_body)
      elsif @rule_type == 'inout'
        rule = Rule.new(@rule_type, @request_method, @request_path, @request_headers, @request_body, @response_status, @response_headers, @response_body)
      end
      if @rule_type != '' && @rules.empty?
        @rules << rule
      elsif @rule_type != '' && !@rules.empty?
        exist = false
        @rules.each do |existing_rule|
          if existing_rule.equals?(rule)
            exist = true
            break
          end
        end
        @rules << rule if exist == false
      end
      requests.shift
      responses.shift
    end
    save_rule
  end

  # This method calls every methods needed to create the rules and checks,
  # if the host address is given, if it's correct and exist.
  def start(path)
    read_file(path)
    if @host == '' || !@host.match(/^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$/) || !@paths.include?(@host)
      puts "Error: the address \"#{@host}\" isn't correct."
      ask_hostaddress
    end
    create_rules
  end
end

# ******************************  Main code  ******************************

hostaddress = nil
filepath = nil
filename = nil
filetype = nil

# Check the command line arguments.
# The "filepath" argument is the only one which needs to be given.
ARGV.each do |arg|
  if arg.include?('-filepath') && filepath.nil?
    filepath = arg.split('=')[1]
  elsif arg.include?('-hostaddress') && hostaddress.nil?
    hostaddress = arg.split('=')[1]
  elsif arg.include?('-filename') && filename.nil?
    filename = arg.split('=')[1]
  elsif arg.include?('-filetype') && filetype.nil?
    filetype = arg.split('=')[1]
    unless filetype == 'json' || filetype == 'yaml'
      puts "Error: the existing file types are 'json' or 'yaml'."
      exit
    end
  else
    puts "How to use the script: (default destination file: 'rules.json')\n=> generator.rb -filepath=<path_to_file> [-hostaddress=<host_address>] [-filename=<dest_file_name>] [-filetype=<dest_file_type('yaml' or 'json')>)\n"
    exit
  end
end

if filepath.nil?
  puts "Error: you have to provide a file with formatted frames with the argument'-filepath=...'."
  exit
end

# If the output file name isn't given, it will be "rules".
# If the output file type isn't given, it will be JSON.
filename = 'rules' if filename.nil?
filetype = 'json' if filetype.nil?

hostaddress = '' if hostaddress.nil?

# Create the generator and start it.
generator = Generator.new(filename, filetype, hostaddress)
generator.start(filepath)
