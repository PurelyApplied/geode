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
BUILD_HANG_REGEX = re.compile('timeout exceeded')
BUILD_HANG_CAPTURE_STACKS = re.compile('Capturing call stacks(.*)timeout exceeded')


class BuildSummary:
    def __init__(self):
        pass


class BuildReport:
    def __init__(self, build_json):
        pass


class Worker:
    pass


def main(url, team, pipeline, job, max_fetch_count, build_count, authorization_cookie):
    session = requests.Session()
    builds = get_builds_summary_sheet(url, team, pipeline, job, max_fetch_count, session, authorization_cookie)

    build_to_examine = get_builds_to_examine(builds, build_count)
    expected_failed_builds_count = sum(b['status'] == 'failed' for b in build_to_examine)
    logging.info(f"Expecting {expected_failed_builds_count} runs to have failure strings.")

    # test name -> [builds, ...]
    failures = {}

    failed_build_count = 0
    for build in tqdm(build_to_examine, desc="Build pages examined"):
        _, this_failure_count = examine_build(authorization_cookie, build, session, url, failures)
        failed_build_count += this_failure_count

    present_results(len(build_to_examine), failed_build_count, failures, url)


def examine_build(authorization_cookie, build, session, url, failures):
    # this_build_failures = {}
    event_response = get_event_response(authorization_cookie, build, session, url)
    logging.debug("Event Status is {}".format(event_response.status_code))

    build_status, event_output = assess_event_response(event_response)
    this_failed_build_count = assess_event_output_for_failure(build, event_output, failures)
    logging.debug("Results: Job status is {}".format(build_status))

    return failures, this_failed_build_count
    # return this_build_failures,this_failed_build_count


def assess_event_output_for_failure(build, event_output, failures):
    n_failures = 0
    for line in event_output.splitlines():
        """Returns true if no failure is found, false if a failure is found."""
        build_fail_matcher = BUILD_FAIL_REGEX.search(line)
        was_failure = bool(build_fail_matcher)
        test_failure_matcher = TEST_FAILURE_REGEX.search(line)
        if test_failure_matcher:
            class_name, method_name = test_failure_matcher.groups()
            this_failure = SingleFailure(class_name, method_name, build)
            test_name = ".".join((this_failure.class_name, this_failure.method))
            if not failures.get(test_name):
                failures[test_name] = [build]
            else:
                failures[test_name].append(build)
            logging.debug(f"Failure identified, {this_failure}")
        n_failures += 1 if was_failure else 0
    return n_failures


def assess_event_response(event_response):
    event_outputs = []
    build_status = 'unknown'
    event_client = sseclient.SSEClient(event_response)
    aggregated_events = []

    for event in event_client.events():
        event_json = json.loads(event.data if event.data else "{}")
        build_status = event_json['data']['status'] if event_json.get('event', 'not-a-status-event') == 'status' else build_status
        if event.event == 'end' or event_json['event'] == 'status' and build_status == 'succeeded':
            return build_status, ''.join(event_outputs)
        elif event.data:
            # event_json = json.loads(event.data)
            if event_json['event'] == 'status':
                # build_status = event_json['data']['status']
                if build_status == 'succeeded':
                    return build_status, ''.join(event_outputs)
            elif event_json['event'] == 'log':
                event_outputs.append(event_json['data']['payload'])

            logging.debug("***************************************************")
            logging.debug("Event *{}* - {}".format(event.event, json.loads(event.data)))
    raise RuntimeError("This should be unreachable.")


def get_event_response(authorization_cookie, build, session, url):
    build_url = '{}{}'.format(url, build['api_url'])
    event_url = '{}/events'.format(build_url)
    event_response = session.get(event_url, cookies=authorization_cookie, stream=True, timeout=60)
    return event_response


def present_results(completed, failed, failures, url):
    if failed > 0:
        rate = (completed - failed) * 100 / completed
        print(color("***********************************************************************************", fg='yellow'))
        print(" Overall build success rate:", color(f"{rate:.5f}% ({completed - failed} of {completed})", fg='blue'))
        print(color("***********************************************************************************", fg='yellow'))
        if failures:
            for failure in failures.keys():
                count = len(failures[failure])
                print(color("{}: ".format(failure), fg='cyan'),
                      color("{} failures".format(count), fg='red'),
                      color(
                          "({}% success rate)".format(((completed - count) / completed) * 100),
                          fg='blue'))
                for build in failures[failure]:
                    failed_build_url = (f"{url}/teams/{build['team_name']}/pipelines/"
                                        f"{build['pipeline_name']}/jobs/{build['job_name']}/builds/{build['name']}")
                    print(color(f"  Failed build {build['name']} ", fg='red'),
                          color(f"at {failed_build_url}", fg='magenta', style='bold'))
    else:
        print(color("No failures! 100% success rate", fg='green', style='bold'))


class SingleFailure:
    def __init__(self, class_name, method, build_json):
        self.class_name = class_name
        self.method = method
        self.build_json = build_json

    def __str__(self):
        return f"Failure({self.class_name}, {self.method}, ({self.build_json['name']} ...))"


def check_line_for_failure(build, failures, line) -> bool:
    """Returns true if no failure is found, false if a failure is found."""
    build_fail_matcher = BUILD_FAIL_REGEX.search(line)
    was_failure = bool(build_fail_matcher)
    test_failure_matcher = TEST_FAILURE_REGEX.search(line)
    if test_failure_matcher:
        class_name, method_name = test_failure_matcher.groups()
        this_failure = SingleFailure(class_name, method_name, build)
        test_name = ".".join((this_failure.class_name, this_failure.method))
        if not failures.get(test_name):
            failures[test_name] = [build]
        else:
            failures[test_name].append(build)
        logging.debug(f"Failure identified, {this_failure}")
    return was_failure


def get_builds_to_examine(builds, build_count):
    """
    :param builds: Build summary JSON
    :param build_count: number of completed builds to return.  Return all if 0
    """
    # possible build statuses:
    statuses = ['succeeded', 'failed', 'aborted', 'errored', 'pending', 'started']
    # TODO: verify 'pending'
    succeeded, failed, aborted, errored, pending, started = sieve(builds, lambda b: b['status'], *statuses)
    completed_builds = succeeded + failed
    completed_builds.sort(key=itemgetter('id'), reverse=True)
    builds_to_analyze = completed_builds[:build_count] if build_count else completed_builds
    logging.info(f"{len(aborted)} aborted builds in examination range: {list_and_sort_by_name(aborted)}")
    logging.info(f"{len(errored)} errored builds in examination range: {list_and_sort_by_name(errored)}")
    logging.info(f"{len(pending)} pending builds in examination range: {list_and_sort_by_name(pending)}")
    logging.info(f"{len(started)} started builds in examination range: {list_and_sort_by_name(started)}")

    if build_count and len(completed_builds) < build_count:
        raise RuntimeError(
            "The build report returned the {} most recent builds, with only {} of these completed.  "
            "This cannot satisfy the desired target of {} jobs to analyze.".format(
                len(builds), len(completed_builds), build_count))

    first_build = builds_to_analyze[-1]['name']
    last_build = builds_to_analyze[0]['name']
    logging.info(f"{len(started)} completed builds to examine, ranging "
                 f"{first_build} - {last_build}: {list_and_sort_by_name(builds_to_analyze)}")
    logging.info(f"{len(failed)} expected failures: {list_and_sort_by_name(failed)}")
    return builds_to_analyze


def list_and_sort_by_name(builds):
    return sorted([int(b['name']) for b in builds], reverse=True)


def get_builds_summary_sheet(url, team, pipeline, job, max_fetch_count, session, authorization_cookie):
    if max_fetch_count == 0:
        # Snoop the top result's name to discover the number of jobs that have been queued.
        snoop = get_builds_summary_sheet(url, team, pipeline, job, 1, session, authorization_cookie)
        max_fetch_count = int(snoop[0]['name'])
        logging.info(f"Snooped: fetching a full history of {max_fetch_count} builds.")

    builds_url = '{}/api/v1/teams/{}/pipelines/{}/jobs/{}/builds'.format(url, team, pipeline, job)
    build_params = {'limit': max_fetch_count}
    build_response = session.get(builds_url, cookies=authorization_cookie, params=build_params)
    if build_response.status_code != 200:
        raise IOError("Initial build summary query returned status code {build_response.status_code}.")
    return build_response.json()


def sieve(iterable, inspector, *keys):
    s = {k: [] for k in keys}
    for item in iterable:
        k = inspector(item)
        if k not in s:
            raise KeyError(f"Unexpected key <{k}> found by inspector in sieve.")
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
                        help="Number of completed jobs to examine.  Enter 0 to examine all jobs fetched.",
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

    main(args.url, args.team, args.pipeline, args.job, args.fetch, args.limit, args.cookie_token)
