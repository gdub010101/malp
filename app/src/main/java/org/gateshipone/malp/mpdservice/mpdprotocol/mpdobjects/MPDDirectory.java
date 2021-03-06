/*
 * Copyright (C) 2016  Hendrik Borghorst
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects;


public class MPDDirectory extends MPDFileEntry implements MPDGenericItem {

    public MPDDirectory(String path) {
        super(path);
    }

    @Override
    public String getSectionTitle() {
        String title = mPath;
        String[] pathSplit = title.split("/");
        if ( pathSplit.length > 0 ) {
            title = pathSplit[pathSplit.length - 1];
        }
        return title;
    }

    public int compareTo(MPDDirectory another) {
        if ( another == null ) {
            return -1;
        }
        return getSectionTitle().toLowerCase().compareTo(another.getSectionTitle().toLowerCase());
    }
}
