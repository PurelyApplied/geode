#!/usr/bin/env python3
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import argparse
import json
import logging
import re
from operator import itemgetter
from urllib.parse import urlparse

import requests
import sseclient
from colors import color
from tqdm import tqdm

BUILD_FAIL_REGEX = re.compile('BUILD FAILED|Test Failed!')
TEST_FAILURE_REGEX = re.compile('(\S+)\s*>\s*(\S+).*FAILED')

def main(url, pipeline, job, build_count, team, max_fetch_count, authorization_cookie):
    session = requests.Session()
    builds = get_builds_summary_sheet(authorization_cookie, job, max_fetch_count, pipeline, session, team, url)

    completed_builds = get_builds_to_analyze(build_count, builds, max_fetch_count)

    failures = {} # test name -> [builds, ...]

    failed_build_count = 0
    for build in tqdm(completed_builds, desc="Build pages examined"):
        failed_build_count = examine_build(authorization_cookie, build, failures, session, url)

    present_results(build_count, failed_build_count, failures, url)


def examine_build(authorization_cookie, build, failures, session, url):
    event_response = get_event_response(authorization_cookie, build, session, url)
    logging.debug("Event Status is {}".format(event_response.status_code))

    build_status, event_output = assess_event_response(event_response)
    failed_build_count = assess_event_output_for_failure(build, event_output, failures)
    logging.debug("Results: Job status is {}".format(build_status))

    return failed_build_count


def assess_event_output_for_failure(build, event_output, failures):
    n_failures = 0
    for line in event_output.splitlines():
        was_failure = check_line_for_failure(build, failures, line)
        n_failures += 1 if was_failure else 0
    return n_failures


def assess_event_response(event_response):
    event_output = ''
    build_status = 'unknown'
    event_client = sseclient.SSEClient(event_response)
    for event in event_client.events():
        if event.event == 'end':
            break
        if event.data:
            event_json = json.loads(event.data)
            event_data = event_json['data']
            if event_json['event'] == 'status':
                build_status = event_data['status']
                if build_status == 'succeeded':
                    break
            if event_json['event'] == 'log':
                event_output += event_data['payload']

            logging.debug("***************************************************")
            logging.debug("Event *{}* - {}".format(event.event, json.loads(event.data)))
    return build_status, event_output


def get_event_response(authorization_cookie, build, session, url):
    build_url = '{}{}'.format(url, build['api_url'])
    event_url = '{}/events'.format(build_url)
    event_response = session.get(event_url, cookies=authorization_cookie, stream=True, timeout=60)
    return event_response


def present_results(completed_build_count, failed_build_count, failures, url):
    if failed_build_count > 0:
        print(color("***********************************************************************************", fg='yellow'))
        print(" Overall build success rate: ",
              color("{}%".format((completed_build_count - failed_build_count) * 100 / completed_build_count),
                    fg='blue'))
        print(color("***********************************************************************************", fg='yellow'))
        if failures:
            for failure in failures.keys():
                count = len(failures[failure])
                print(color("{}: ".format(failure), fg='cyan'),
                      color("{} failures".format(count), fg='red'),
                      color(
                          "({}% success rate)".format(((completed_build_count - count) / completed_build_count) * 100),
                          fg='blue'))
                for build in failures[failure]:
                    print(color("  Failed build {} ".format(build['name']), fg='red'),
                          color("at {}/teams/{}/pipelines/{}/jobs/{}/builds/{}".format(url,
                                                                                       build['team_name'],
                                                                                       build['pipeline_name'],
                                                                                       build['job_name'],
                                                                                       build['name']),
                                fg='magenta', style='bold'))
    else:
        print(color("No failures! 100% success rate", fg='green', style='bold'))


def check_line_for_failure(build, failures, line) -> bool:
    """Returns true if no failure is found, false if a failure is found."""
    build_fail_matcher = BUILD_FAIL_REGEX.search(line)
    was_failure = bool(build_fail_matcher)
    test_failure_matcher = TEST_FAILURE_REGEX.search(line)
    if test_failure_matcher:
        test_name = ".".join(test_failure_matcher.groups())
        if not failures.get(test_name):
            failures[test_name] = [build]
        else:
            failures[test_name].append(build)
        logging.debug("Failure information: {} - {}".format(*test_failure_matcher.groups()))
    return was_failure


def get_builds_to_analyze(build_count, builds, max_fetch_count):
    # possible build statuses:
    # {'failed', 'aborted', 'succeeded', 'errored'}
    # Probably also 'pending'

    succeeded, failed, aborted, errored, pending = sieve(builds, lambda b: b['status'], 'succeeded', 'failed', 'aborted', 'errored', 'pending')
    completed_builds = succeeded + failed
    completed_builds.sort(key=itemgetter('id'), reverse=True)
    builds_to_analyze = completed_builds[:build_count]
    logging.info(f"Of {len(builds)} runs examined: {len(succeeded)} succeeded, {len(failed)} failed, {len(aborted)} aborted, {len(errored)} errored, {len(pending)} pending")

    if len(completed_builds) < build_count:
        raise RuntimeError(
            "The build report fetch was limited to the {} most recent builds, with only {} of these completed.  "
            "This cannot satisfy the desired target of {} jobs to analyze.".format(
                max_fetch_count, len(completed_builds), build_count))

    logging.info(f"Examining {build_count} most recent completed builds: {builds_to_analyze[-1]['name']} - {builds_to_analyze[0]['name']}")
    return builds_to_analyze


def get_builds_summary_sheet(authorization_cookie, job, max_fetch_count, pipeline, session, team, url):
    builds_url = '{}/api/v1/teams/{}/pipelines/{}/jobs/{}/builds'.format(url, team, pipeline, job)
    build_params = {'limit': max_fetch_count}
    build_response = session.get(builds_url, cookies=authorization_cookie, params=build_params)
    return build_response.json()


def sieve(iterable, inspector, *keys):
    s = {k: [] for k in keys}
    for item in iterable:
        k = inspector(item)
        if k not in s:
            raise KeyError(f"Unexpected key {k} found by inspector in sieve.")
        s[inspector(item)].append(item)
    return [s[k] for k in keys]


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('url',
                        help="URL to Concourse.",
                        type=lambda s: str(s).rstrip('/'))
    parser.add_argument('pipeline',
                        help="Name of pipeline.",
                        type=str)
    parser.add_argument('job',
                        help="Name of job.",
                        type=str)
    parser.add_argument('limit',
                        help="Number of completed jobs to examine.",
                        type=int,
                        nargs='?',
                        default=50)
    parser.add_argument('team',
                        help="Team to which the provided pipeline belongs.",
                        type=str,
                        nargs='?',
                        default="main")
    parser.add_argument('fetch',
                        help="Limit size of initial build-status page.",
                        type=int,
                        nargs='?',
                        default=100)
    parser.add_argument('--cookie-token',
                        help="If authentication is required, provide your ATC-Authorization cookie's token here.  "
                             "Unfortunately, this is currently done by logging in via a web browser, "
                             "inspecting your cookies, and pasting it here.",
                        type=lambda t: {u'ATC-Authorization':
                                            '"Bearer {}"'.format(t if not t.startswith("Bearer ") else t[7:])})
    parser.add_argument('--debug',
                        help="Enable debug logging.  Implies --verbose",
                        action="store_true")
    parser.add_argument('--verbose',
                        help="Enable info logging",
                        action="store_true")

    args = parser.parse_args()
    # Validation
    concourse_url = urlparse(args.url)
    if not concourse_url.scheme or not concourse_url.netloc or concourse_url.path != '':
        print(color("Url {} seems to be invalid. Please check your arguments.".format(args.url), fg='red'))
        exit(1)

    assert args.fetch >= args.limit, "Fetching fewer jobs than you will analyze is pathological."

    if args.debug:
        logging.getLogger().setLevel(logging.DEBUG)
    elif args.verbose:
        logging.getLogger().setLevel(logging.INFO)

    main(args.url, args.pipeline, args.job, args.limit, args.team, args.fetch, args.cookie_token)
