/*
 * Copyright (c) 2012, 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.metalib.net.url.jersey;

import org.metalib.net.url.jersey.l10n.LocalizationMessages;

/**
 * Parser for string URI with template parameters which produces {@link java.net.URI URIs} from Strings.
 * Example of parsed uri: {@code "http://user@{host}:{port}/a/{path}?query=1#fragment"}.
 * The parser is not thread safe.
 *
 * @author Paul Sandoz
 * @author Marek Potociar
 */
class UriParser {
    private static final String ERROR_STATE = "URI_PARSER_NOT_EXECUTED";
    private final String input;
    private CharacterIterator ci;
    private String scheme;
    private String userInfo;
    private String host;
    private String port;
    private String query;
    private String path;
    private String fragment;
    private String ssp;
    private String authority;
    private boolean opaque;
    private boolean parserExecuted;


    /**
     * Creates new parser initialized with {@code uri}.
     *
     * @param uri String with URI to be parsed. May contain template parameters.
     */
    UriParser(String uri) {
        this.input = uri;
    }

    private String parseComponentWithIP(String delimiters, boolean mayEnd) {
        return parseComponent(delimiters, mayEnd, true);
    }


    private String parseComponent(String delimiters, boolean mayEnd) {
        return parseComponent(delimiters, mayEnd, false);
    }

    /**
     * Parses the URI component. Parsing starts at position of the first character of
     * component and ends with position of one of the delimiters. The string and current
     * position is taken from the {@link CharacterIterator}.
     *
     * @param delimiters String with delimiters which terminates the component.
     * @param mayEnd     True if component might be the last part of the URI.
     * @param isIp       True if the component might contain IPv6 address.
     * @return Extracted component.
     */
    private String parseComponent(String delimiters, boolean mayEnd, boolean isIp) {

        int curlyBracketsCount = 0;
        int squareBracketsCount = 0;

        StringBuilder sb = new StringBuilder();

        boolean endOfInput = false;
        char c = ci.current();
        while (!endOfInput) {
            if (c == '{') {
                curlyBracketsCount++;
                sb.append(c);
            } else if (c == '}') {
                curlyBracketsCount--;
                sb.append(c);
            } else if (isIp && c == '[') {
                squareBracketsCount++;
                sb.append(c);
            } else if (isIp && c == ']') {
                squareBracketsCount--;
                sb.append(c);

                // test IPv6 or regular expressions in the template params
            } else if ((!isIp || squareBracketsCount == 0) && (curlyBracketsCount == 0)
                       && (delimiters != null && delimiters.indexOf(c) >= 0)) {
                return sb.length() == 0 ? null : sb.toString();
            } else {
                sb.append(c);
            }
            endOfInput = !ci.hasNext();
            if (!endOfInput) {
                c = ci.next();
            }
        }
        if (mayEnd) {
            return sb.length() == 0 ? null : sb.toString();
        }
        throw new IllegalArgumentException(LocalizationMessages.URI_PARSER_COMPONENT_DELIMITER(delimiters, ci.pos()));
    }

    /**
     * Parses the input string URI. After calling this method The result components can be retrieved by calling appropriate
     * getter methods like {@link #getHost()}, {@link #getPort()}, etc.
     */
    public void parse() {
        this.parserExecuted = true;
        this.ci = new CharacterIterator(input);
        if (!ci.hasNext()) {
            // empty string on input -> set both SSP and path to ""
            this.path = "";
            this.ssp = "";
            return;
        }
        ci.next();
        String comp = parseComponent(":/?#", true);

        if (ci.hasNext()) {
            this.ssp = ci.getInput().substring(ci.pos() + 1);
        }

        this.opaque = false;
        if (ci.current() == ':') {
            // absolute
            if (comp == null) {
                throw new IllegalArgumentException(LocalizationMessages.URI_PARSER_SCHEME_EXPECTED(ci.pos(), input));
            }
            scheme = comp;
            if (!ci.hasNext()) {
                // empty SSP/path -> set both SSP and path to ""
                this.path = "";
                this.ssp = "";
                return;
            }
            char c = ci.next();
            if (c == '/') {
                // hierarchical
                parseHierarchicalUri();

            } else {
                // opaque
                this.opaque = true;
            }
        } else {
            ci.setPosition(0);
            // relative
            if (ci.current() == '/') {
                parseHierarchicalUri();
            } else {
                parsePath();
            }
        }
    }

    private void parseHierarchicalUri() {
        if (ci.hasNext() && ci.peek() == '/') {
            // authority
            ci.next();
            ci.next();
            parseAuthority();

        }
        if (!ci.hasNext()) {
            if (ci.current() == '/') {
                path = "/";
            }
            return;
        }
        parsePath();
    }

    private void parseAuthority() {
        int start = ci.pos();
        String comp = parseComponentWithIP("@/?#", true);
        if (ci.current() == '@') {
            this.userInfo = comp;
            if (!ci.hasNext()) {
                return;
            }
            ci.next();
            comp = parseComponentWithIP(":/?#", true);
        } else {
            ci.setPosition(start);
            comp = parseComponentWithIP("@:/?#", true);
        }

        this.host = comp;

        if (ci.current() == ':') {
            if (!ci.hasNext()) {
                return;
            }
            ci.next();
            this.port = parseComponent("/?#", true);
        }
        this.authority = ci.getInput().substring(start, ci.pos());
        if (this.authority.length() == 0) {
            this.authority = null;
        }


    }


    private void parsePath() {
        this.path = parseComponent("?#", true);


        if (ci.current() == '?') {
            if (!ci.hasNext()) {
                return;
            }
            ci.next(); // skip ?

            this.query = parseComponent("#", true);
        }

        if (ci.current() == '#') {
            if (!ci.hasNext()) {
                return;
            }
            ci.next(); // skip #

            this.fragment = parseComponent(null, true);
        }
    }

    /**
     * Returns parsed scheme specific part. The {@link #parse() method} must be called before executing this method.
     *
     * @return Scheme specific part.
     */
    public String getSsp() {
        if (!parserExecuted) {
            throw new IllegalStateException(ERROR_STATE);
        }
        return ssp;
    }

    /**
     * Returns parsed scheme component. The {@link #parse() method} must be called before executing this method.
     *
     * @return Scheme.
     */
    public String getScheme() {
        if (!parserExecuted) {
            throw new IllegalStateException(ERROR_STATE);
        }
        return scheme;
    }

    /**
     * Returns parsed user info component. The {@link #parse() method} must be called before executing this method.
     *
     * @return User info.
     */
    public String getUserInfo() {
        if (!parserExecuted) {
            throw new IllegalStateException(ERROR_STATE);
        }
        return userInfo;
    }

    /**
     * Returns parsed host component. The {@link #parse() method} must be called before executing this method.
     *
     * @return Host.
     */
    public String getHost() {
        if (!parserExecuted) {
            throw new IllegalStateException(ERROR_STATE);
        }
        return host;
    }

    /**
     * Returns parsed port component. The {@link #parse() method} must be called before executing this method.
     *
     * @return Port.
     */
    public String getPort() {
        if (!parserExecuted) {
            throw new IllegalStateException(ERROR_STATE);
        }
        return port;
    }

    /**
     * Returns parsed query component. The {@link #parse() method} must be called before executing this method.
     *
     * @return Query.
     */
    public String getQuery() {
        if (!parserExecuted) {
            throw new IllegalStateException(ERROR_STATE);
        }
        return query;
    }

    /**
     * Returns parsed path component. The {@link #parse() method} must be called before executing this method.
     *
     * @return Path.
     */
    public String getPath() {
        if (!parserExecuted) {
            throw new IllegalStateException(ERROR_STATE);
        }
        return path;
    }

    /**
     * Returns parsed fragment component. The {@link #parse() method} must be called before executing this method.
     *
     * @return Fragment.
     */
    public String getFragment() {
        if (!parserExecuted) {
            throw new IllegalStateException(ERROR_STATE);
        }
        return fragment;
    }

    /**
     * Returns parsed authority component. The {@link #parse() method} must be called before executing this method.
     *
     * @return Authority.
     */
    public String getAuthority() {
        if (!parserExecuted) {
            throw new IllegalStateException(ERROR_STATE);
        }
        return authority;
    }

    /**
     * Returns whether the input string URI is opaque. The {@link #parse() method} must be called before executing this method.
     *
     * @return True if the uri is opaque.
     */
    public boolean isOpaque() {
        if (!parserExecuted) {
            throw new IllegalStateException(ERROR_STATE);
        }
        return opaque;
    }
}
